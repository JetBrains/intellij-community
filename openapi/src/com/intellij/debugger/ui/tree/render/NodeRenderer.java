package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.util.JDOMExternalizable;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface NodeRenderer extends ChildrenRenderer, ValueLabelRenderer {
  String getName();

  void setName(String text);

  NodeRenderer clone();
}
