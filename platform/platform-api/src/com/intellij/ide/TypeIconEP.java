// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

final class TypeIconEP implements PluginAware {
  @Transient
  private PluginDescriptor pluginDescriptor;

  @Attribute("className")
  @RequiredElement
  public String className;

  @Attribute("icon")
  @RequiredElement
  public String icon;

  @Transient final NullableLazyValue<Icon> lazyIcon =
    lazyNullable(() -> IconLoader.findIcon(icon, pluginDescriptor != null ? pluginDescriptor.getClassLoader() : getClass().getClassLoader()));

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
