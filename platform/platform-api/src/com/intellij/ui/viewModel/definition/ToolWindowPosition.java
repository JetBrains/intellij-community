// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import com.intellij.openapi.wm.ToolWindowAnchor;

public class ToolWindowPosition {
  private final ToolWindowAnchor myToolWindowAnchor;
  private final boolean myIsSideTool;

  public ToolWindowPosition(ToolWindowAnchor toolWindowAnchor, boolean isSideTool) {

    myToolWindowAnchor = toolWindowAnchor;
    myIsSideTool = isSideTool;
  }

  public ToolWindowAnchor getToolWindowAnchor() {
    return myToolWindowAnchor;
  }

  public boolean isSideTool() {
    return myIsSideTool;
  }
}
