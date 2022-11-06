// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WeigherExtensionPoint extends BaseKeyedLazyInstance<Weigher> implements KeyedLazyInstance<Weigher> {
  public static final ExtensionPointName<WeigherExtensionPoint> EP = new ExtensionPointName<>("com.intellij.weigher");

  // these must be public for scrambling compatibility
  @Attribute("key")
  @RequiredElement
  public String key;

  @Attribute("implementationClass")
  @RequiredElement
  public String implementationClass;

  @Attribute("id")
  public String id;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  @Override
  public @NotNull Weigher createInstance(@NotNull ComponentManager componentManager, @NotNull PluginDescriptor pluginDescriptor) {
    Weigher weigher = super.createInstance(componentManager, pluginDescriptor);
    weigher.setDebugName(id);
    return weigher;
  }

  @Override
  public String getKey() {
    return key;
  }
}
