/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.datasource.core.internal;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jndi.JNDIContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.datasource.core.DataSourceManager;
import org.wso2.carbon.datasource.core.api.DataSourceManagementService;
import org.wso2.carbon.datasource.core.api.DataSourceService;
import org.wso2.carbon.datasource.core.exception.DataSourceException;
import org.wso2.carbon.datasource.core.impl.DataSourceManagementServiceImpl;
import org.wso2.carbon.datasource.core.impl.DataSourceServiceImpl;
import org.wso2.carbon.datasource.core.spi.DataSourceReader;
import org.wso2.carbon.kernel.configprovider.ConfigProvider;
import org.wso2.carbon.kernel.startupresolver.RequiredCapabilityListener;

import java.util.HashMap;
import java.util.Map;

/**
 * DataSourceListenerComponent implements RequiredCapabilityListener interface. This wait till all the DataSourceReader
 * components are registered and then initialize the DataSourceManager. Followed by register the DataSourceService and
 * DataSourceManagementService.
 */
@Component(
        name = "org.wso2.carbon.kernel.datasource.core.internal.DataSourceListenerComponent",
        immediate = true,
        property = {
                "componentName=carbon-datasource-service"
        }
)
public class DataSourceListenerComponent implements RequiredCapabilityListener {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceListenerComponent.class);

    private BundleContext bundleContext;
    private Map<String, DataSourceReader> readers = new HashMap<>();
    private ConfigProvider configProvider;

    @Activate
    protected void start(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Reference(
            name = "org.wso2.carbon.datasource.jndi",
            service = JNDIContextManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unRegisterJNDIContextManager"
    )
    protected void registerJNDIContextManager(JNDIContextManager jndiContextManager) {
    }

    protected void unRegisterJNDIContextManager(JNDIContextManager jndiContextManager) {
    }

    @Reference(
            name = "carbon.datasource.DataSourceReader",
            service = DataSourceReader.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unregisterReader"
    )
    protected void registerReader(DataSourceReader reader) {
        readers.put(reader.getType(), reader);
    }

    protected void unregisterReader(DataSourceReader reader) {
        readers.remove(reader.getType());
    }

    /**
     * Get the ConfigProvider service.
     * This is the bind method that gets called for ConfigProvider service registration that satisfy the policy.
     *
     * @param configProvider the ConfigProvider service that is registered as a service.
     */
    @Reference(
            name = "carbon.config.provider",
            service = ConfigProvider.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unregisterConfigProvider"
    )
    protected void registerConfigProvider(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * This is the unbind method for the above reference that gets called for ConfigProvider instance un-registrations.
     *
     * @param configProvider the ConfigProvider service that get unregistered.
     */
    protected void unregisterConfigProvider(ConfigProvider configProvider) {
        this.configProvider = null;
    }

    @Override
    public void onAllRequiredCapabilitiesAvailable() {
        try {
            DataSourceManager dataSourceManager = DataSourceManager.getInstance();
            dataSourceManager.initDataSources(configProvider, readers);

            DataSourceService dsService = new DataSourceServiceImpl();
            bundleContext.registerService(DataSourceService.class, dsService, null);

            DataSourceManagementService dataSourceMgtService = new DataSourceManagementServiceImpl();
            bundleContext.registerService(DataSourceManagementService.class, dataSourceMgtService, null);
        } catch (DataSourceException e) {
            logger.error("Error occurred while initializing data sources", e);
        }
    }
}
