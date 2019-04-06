// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.util.registry.Registry;

/**
 * @author egor
 */
public class FileObjectRenderer extends CompoundReferenceRenderer {
  public FileObjectRenderer() {
    super("File", null, NodeRendererSettings.createExpressionChildrenRenderer("listFiles()", null));
    setClassName("java.io.File");
    setEnabled(true);
  }

  @Override
  public boolean isEnabled() {
    return Registry.is("debugger.renderers.file");
  }
}
