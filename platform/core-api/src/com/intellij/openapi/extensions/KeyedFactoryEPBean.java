// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

public final class KeyedFactoryEPBean implements PluginAware {
  private transient PluginDescriptor pluginDescriptor;

  @SuppressWarnings("unused")
  private KeyedFactoryEPBean() {
  }

  @NonInjectable
  public KeyedFactoryEPBean(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }

  // these must be public for scrambling compatibility
  @Attribute("key")
  public String key;

  @Attribute("implementationClass")
  public String implementationClass;

  @Attribute("factoryClass")
  public String factoryClass;

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}