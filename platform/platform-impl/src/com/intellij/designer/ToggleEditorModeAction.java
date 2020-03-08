/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
    super(anchor != null ? StringUtil.capitalize(anchor.toString()) : IdeBundle.message("action.text.editor.mode.none"),
          anchor != null ? IdeBundle.message("action.description.pin.tool.window.to.0.side.ui.designer.editor", anchor)
                         : IdeBundle.message("action.description.unpin.tool.window.from.designer.editor"), null);
    myManager = manager;
    myProject = project;
    myAnchor = anchor;
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