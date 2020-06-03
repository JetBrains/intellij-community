// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

public class FileObjectRenderer implements NodeRendererProvider {
  @Override
  public @NotNull NodeRenderer createRenderer() {
    return new RendererBuilder("File")
      .childrenRenderer(NodeRendererSettings.createExpressionChildrenRenderer("listFiles()", null))
      .isApplicableForInheritors("java.io.File")
      .enabled(Registry.is("debugger.renderers.file"))
      .build();
  }
}
