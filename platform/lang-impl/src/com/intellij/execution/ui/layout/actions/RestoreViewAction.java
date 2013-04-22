/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.CellTransform;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ui.content.Content;

public class RestoreViewAction extends AnAction {

  private final Content myContent;
  private final CellTransform.Restore myRestoreAction;

  public RestoreViewAction(final Content content, CellTransform.Restore restore) {
    myContent = content;
    myRestoreAction = restore;
  }

  @Override
  public void update(final AnActionEvent e) {
    Presentation p = e.getPresentation();
    p.setText(ActionsBundle.message("action.Runner.RestoreView.text", myContent.getDisplayName()));
    p.setDescription(ActionsBundle.message("action.Runner.RestoreView.description"));
    p.setIcon(myContent.getIcon());
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    myRestoreAction.restoreInGrid();
  }

  public Content getContent() {
    return myContent;
  }
}
