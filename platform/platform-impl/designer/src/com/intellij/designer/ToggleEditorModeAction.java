// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public abstract class ToggleEditorModeAction extends ToggleAction {
  protected final LightToolWindowManager myManager;
  protected final Project myProject;
  private final ToolWindowAnchor myAnchor;

  public ToggleEditorModeAction(LightToolWindowManager manager, Project project, ToolWindowAnchor anchor) {
    super(anchor != null ? anchor.getCapitalizedDisplayName() : IdeBundle.message("action.text.editor.mode.none"),
          anchor != null ? IdeBundle.message("action.description.pin.tool.window.to.0.side.ui.designer.editor", anchor.getDisplayName())
                         : IdeBundle.message("action.description.unpin.tool.window.from.designer.editor"), null);
    myManager = manager;
    myProject = project;
    myAnchor = anchor;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myAnchor == myManager.getEditorMode();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    if (state) {
      myManager.setEditorMode(myAnchor);

      LightToolWindowManager manager = getOppositeManager();
      if (manager.getEditorMode() == myAnchor) {
        manager.setEditorMode(myAnchor == ToolWindowAnchor.LEFT ? ToolWindowAnchor.RIGHT : ToolWindowAnchor.LEFT);
      }
    }
    else {
      myManager.setEditorMode(null);
    }
  }

  protected abstract LightToolWindowManager getOppositeManager();
}