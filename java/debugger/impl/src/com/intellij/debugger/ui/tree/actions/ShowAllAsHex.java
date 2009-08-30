package com.intellij.debugger.ui.tree.actions;

import com.intellij.debugger.ui.tree.render.HexRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.debugger.settings.NodeRendererSettings;

import java.util.List;
import java.util.Iterator;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ShowAllAsHex extends ShowAllAs {

  public ShowAllAsHex() {
    super(NodeRendererSettings.getInstance().getHexRenderer());
  }
}
