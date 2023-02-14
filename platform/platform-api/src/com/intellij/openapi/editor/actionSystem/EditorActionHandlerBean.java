// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

/**
 * Registers action invoked in the editor.
 */
public final class EditorActionHandlerBean implements PluginAware {
  PluginDescriptor pluginDescriptor;

  /**
   * Action ID.
   */
  @Attribute("action")
  @RequiredElement
  public String action;

  @Attribute("implementationClass")
  @RequiredElement
  public String implementationClass;

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
