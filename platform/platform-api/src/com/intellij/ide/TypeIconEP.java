// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

final class TypeIconEP implements PluginAware {
  @Transient
  private PluginDescriptor pluginDescriptor;

  @Attribute("className")
  @RequiredElement
  public String className;

  @Attribute("icon")
  @RequiredElement
  public String icon;

  @Transient final NullableLazyValue<Icon> lazyIcon = NullableLazyValue.createValue(() -> {
    return IconLoader.findIcon(icon, pluginDescriptor == null ? getClass().getClassLoader() : pluginDescriptor.getPluginClassLoader());
  });

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
