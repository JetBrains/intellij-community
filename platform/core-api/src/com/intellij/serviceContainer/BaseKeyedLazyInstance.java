// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class BaseKeyedLazyInstance<T> implements PluginAware {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private PluginDescriptor pluginDescriptor;

  private volatile T instance;

  protected BaseKeyedLazyInstance() {
  }

  @TestOnly
  protected BaseKeyedLazyInstance(@NotNull T instance) {
    this.instance = instance;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }

  @Nullable
  protected abstract String getImplementationClassName();

  @NotNull
  public final T getInstance() {
    T result = instance;
    if (result != null) {
      return result;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      result = instance;
      if (result != null) {
        return result;
      }

      result = createInstance(ApplicationManager.getApplication());
      instance = result;
    }
    return result;
  }

  @NotNull
  public T createInstance(@NotNull ComponentManager componentManager) {
    return componentManager.instantiateExtensionWithPicoContainerOnlyIfNeeded(getImplementationClassName(), pluginDescriptor);
  }

  @Transient
  @NotNull
  public final PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  // todo get rid of it - pluginDescriptor must be not null
  @NotNull
  public ClassLoader getLoaderForClass() {
    return pluginDescriptor == null ? getClass().getClassLoader() : pluginDescriptor.getPluginClassLoader();
  }
}
