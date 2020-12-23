// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ServiceBean implements PluginAware {
  private static final Logger LOG = Logger.getInstance(ServiceBean.class);

  @Attribute
  public String serviceInterface;
  private PluginDescriptor myPluginDescriptor;

  public static <T> List<T> loadServicesFromBeans(@NotNull ExtensionPointName<ServiceBean> epName, @NotNull Class<T> componentClass) {
    final List<T> components = new ArrayList<>();
    for (ServiceBean exportableBean : epName.getExtensionList()) {
      final String serviceClass = exportableBean.serviceInterface;
      if (serviceClass == null) {
        LOG.error("Service interface not specified in " + epName);
        continue;
      }
      try {
        final Class<?> aClass = Class.forName(serviceClass, true, exportableBean.getPluginDescriptor().getPluginClassLoader());
        final Object service = ApplicationManager.getApplication().getService(aClass);
        if (service == null) {
          LOG.error("Can't find service: " + serviceClass);
          continue;
        }
        if (!componentClass.isInstance(service)) {
          LOG.error("Service " + serviceClass + " is registered in " + epName.getName() + " EP, but doesn't implement " + componentClass.getName());
          continue;
        }

        components.add((T)service);
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
    }
    return components;
  }

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
