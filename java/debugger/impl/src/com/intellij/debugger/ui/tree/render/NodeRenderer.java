// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;

/**
 * Allows to register custom value renderer.
 * <p>It is recommended to use {@link CompoundRendererProvider} instead
 */
public interface NodeRenderer extends ChildrenRenderer, ValueLabelRenderer {
  ExtensionPointName<NodeRenderer> EP_NAME = ExtensionPointName.create("com.intellij.debugger.nodeRenderer");

  @Nls String getName();

  void setName(String text);

  boolean isEnabled();

  void setEnabled(boolean enabled);
}
