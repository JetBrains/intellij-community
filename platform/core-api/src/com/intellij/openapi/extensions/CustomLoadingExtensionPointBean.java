// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class CustomLoadingExtensionPointBean<T> extends BaseKeyedLazyInstance<T> {
  @Attribute
  public String factoryClass;

  @Attribute
  public String factoryArgument;

  protected CustomLoadingExtensionPointBean() {
  }

  @TestOnly
  protected CustomLoadingExtensionPointBean(@NotNull T instance) {
    super(instance);
  }

  public final @NotNull T createInstance(@NotNull ComponentManager componentManager) {
    T instance;
    if (factoryClass == null) {
      instance = super.createInstance(componentManager, getPluginDescriptor());
    }
    else {
      ExtensionFactory factory = componentManager.instantiateClass(factoryClass, getPluginDescriptor());
      //noinspection unchecked
      instance = (T)factory.createInstance(factoryArgument, getImplementationClassName());
    }

    if (instance instanceof PluginAware) {
      ((PluginAware)instance).setPluginDescriptor(getPluginDescriptor());
    }
    return instance;
  }
}
