// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.memory.component.MemoryViewManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;

/**
 * @author Vitaliy.Bibaev
 */
public class SwitchUpdateModeAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return MemoryViewManager.getInstance().isAutoUpdateModeEnabled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project != null) {
      MemoryViewManager.getInstance().setAutoUpdate(state);
    }
  }
}
