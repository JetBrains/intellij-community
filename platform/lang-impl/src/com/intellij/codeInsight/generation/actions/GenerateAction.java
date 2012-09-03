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

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

public class GenerateAction extends DumbAwareAction implements PreloadableAction {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    final ListPopup popup =
      JBPopupFactory.getInstance().createActionGroupPopup(
          CodeInsightBundle.message("generate.list.popup.title"),
                                                          getGroup(),
                                                          dataContext,
                                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                          false);

    popup.showInBestPositionFor(dataContext);
  }

  @Override
  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    boolean groupEmpty = ActionGroupUtil.isGroupEmpty(getGroup(), event);
    presentation.setEnabled(!groupEmpty);
  }

  private static DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE);
  }

  @Override
  public void preload() {
    ((ActionManagerImpl) ActionManager.getInstance()).preloadActionGroup(IdeActions.GROUP_GENERATE);
  }
}