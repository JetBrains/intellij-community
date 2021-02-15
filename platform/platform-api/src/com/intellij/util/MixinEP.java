// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.serviceContainer.LazyExtensionInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public final class MixinEP<T> extends LazyExtensionInstance<T> implements PluginAware {
  private transient PluginDescriptor pluginDescriptor;

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  private final NotNullLazyValue<Class<?>> myKey = NotNullLazyValue.lazy(() -> {
    if (key == null) {
      String error = "No key specified for mixin with implementation class " + implementationClass;
      throw new PluginException(error, pluginDescriptor.getPluginId());
    }

    try {
      return ApplicationManager.getApplication().loadClass(key, pluginDescriptor);
    }
    catch (ClassNotFoundException e) {
      throw ApplicationManager.getApplication().createError(e, pluginDescriptor.getPluginId());
    }
  });

  public Class<?> getKey() {
    return myKey.getValue();
  }

  public @NotNull T getInstance() {
    return getInstance(ApplicationManager.getApplication(), pluginDescriptor);
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }
}