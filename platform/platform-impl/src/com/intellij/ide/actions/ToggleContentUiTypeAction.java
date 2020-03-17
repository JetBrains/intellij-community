// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowContentUiType;

final class ToggleContentUiTypeAction extends BaseToolWindowToggleAction {
  @Override
  protected boolean isSelected(ToolWindow window) {
    return window.getContentUiType() == ToolWindowContentUiType.TABBED;
  }

  @Override
  protected void setSelected(ToolWindow window, boolean state) {
    window.setContentUiType(state ? ToolWindowContentUiType.TABBED : ToolWindowContentUiType.COMBO, null);
  }

  @Override
  protected void update(ToolWindow window, Presentation presentation) {
    presentation.setEnabled(window.getContentManager().getContentCount() > 1);
  }
}
