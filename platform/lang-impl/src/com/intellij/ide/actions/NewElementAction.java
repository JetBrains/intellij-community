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

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

public class NewElementAction extends AnAction implements DumbAware {

  public void actionPerformed(final AnActionEvent e) {
    showPopup(e.getDataContext());
  }

  protected void showPopup(DataContext dataContext) {
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(getPopupTitle(),
                              getGroup(),
                              dataContext,
                              false, false, false,
                              null, -1, LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext));

    popup.showInBestPositionFor(dataContext);
  }

  protected String getPopupTitle() {
    return IdeBundle.message("title.popup.new.element");
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (ideView == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(), event));
  }

  private static ActionGroup getGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
  }
}
