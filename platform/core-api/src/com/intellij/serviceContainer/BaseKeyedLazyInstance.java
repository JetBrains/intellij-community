// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class BaseKeyedLazyInstance<T> extends LazyExtensionInstance<T> implements PluginAware {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private PluginDescriptor pluginDescriptor;

  protected BaseKeyedLazyInstance() {
  }

  @TestOnly
  protected BaseKeyedLazyInstance(@NotNull T instance) {
    super(instance);
  }

  @Transient
  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }

  @Override
  protected abstract @Nullable String getImplementationClassName();

  public final @NotNull T getInstance() {
    return getInstance(ApplicationManager.getApplication(), pluginDescriptor);
  }

  // todo get rid of it - pluginDescriptor must be not null
  public @NotNull ClassLoader getLoaderForClass() {
    return pluginDescriptor == null ? getClass().getClassLoader() : pluginDescriptor.getPluginClassLoader();
  }
}
