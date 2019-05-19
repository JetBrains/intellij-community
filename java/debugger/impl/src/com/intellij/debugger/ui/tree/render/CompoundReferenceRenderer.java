// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;

public class CompoundReferenceRenderer extends CompoundTypeRenderer {
  public CompoundReferenceRenderer(NodeRendererSettings rendererSettings,
                                   String name,
                                   ValueLabelRenderer labelRenderer,
                                   ChildrenRenderer childrenRenderer) {
    super(rendererSettings, name, labelRenderer, childrenRenderer);
  }

  public CompoundReferenceRenderer(String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    super(NodeRendererSettings.getInstance(), name, labelRenderer, childrenRenderer);
  }

  @Override
  public boolean isApplicable(Type type) {
    if (type instanceof ReferenceType) {
      return super.isApplicable(type);
    }
    return false;
  }

  @Override
  public boolean hasOverhead() {
    return false;
  }
}
