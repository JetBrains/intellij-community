// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.jsonRpc;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonRpcDomainBean extends BaseKeyedLazyInstance<Object> {
  public static final ExtensionPointName<JsonRpcDomainBean> EP_NAME = new ExtensionPointName<>("org.jetbrains.jsonRpcDomain");

  @Attribute("name")
  public String name;

  @Attribute("implementation")
  public String implementation;

  @Attribute("service")
  public String service;

  @Attribute("overridable")
  public boolean overridable;

  @Override
  public @NotNull Object createInstance(@NotNull ComponentManager componentManager, @NotNull PluginDescriptor pluginDescriptor) {
    if (service == null) {
      return super.createInstance(componentManager, pluginDescriptor);
    }
    else {
      try {
        return ApplicationManager.getApplication().getService(Class.forName(service, true, pluginDescriptor.getPluginClassLoader()));
      }
      catch (Throwable e) {
        throw new PluginException(e, pluginDescriptor.getPluginId());
      }
    }
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementation;
  }
}