// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.serviceContainer.LazyExtensionInstance;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Property(style = Property.Style.ATTRIBUTE)
public final class ModuleTypeEP extends LazyExtensionInstance<ModuleType<?>> implements PluginAware {
  public String id;
  public String implementationClass;
  public boolean classpathProvider;

  private PluginDescriptor pluginDescriptor;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  public @NotNull ModuleType<?> getModuleType() {
    return getInstance(ApplicationManager.getApplication(), pluginDescriptor);
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
