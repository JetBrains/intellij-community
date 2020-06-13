// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.util.registry.Registry;

public class FileObjectRenderer extends CompoundRendererProvider {
  @Override
  protected String getName() {
    return "File";
  }

  @Override
  protected ChildrenRenderer getChildrenRenderer() {
    return NodeRendererSettings.createExpressionChildrenRenderer("listFiles()", null);
  }

  @Override
  protected String getClassName() {
    return "java.io.File";
  }

  @Override
  protected boolean isEnabled() {
    return Registry.is("debugger.renderers.file");
  }
}
