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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class NewElementAction extends AnAction  implements DumbAware {
  public void actionPerformed(final AnActionEvent event) {
    showPopup(event.getDataContext());
  }

  protected void showPopup(DataContext context) {
    createPopup(context).showInBestPositionFor(context);
  }

  protected ListPopup createPopup(DataContext dataContext) {
    return JBPopupFactory.getInstance()
      .createActionGroupPopup(getPopupTitle(),
                              getGroup(dataContext),
                              dataContext,
                              isShowNumbers(),
                              isShowDisabledActions(),
                              isHonorActionMnemonics(),
                              getDisposeCallback(),
                              getMaxRowCount(),
                              getPreselectActionCondition(dataContext));
  }

  protected int getMaxRowCount() {
    return -1;
  }

  @Nullable
  protected Condition<AnAction> getPreselectActionCondition(DataContext dataContext) {
    return LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext);
  }

  @Nullable
  protected Runnable getDisposeCallback() {
    return null;
  }

  protected boolean isHonorActionMnemonics() {
    return false;
  }

  protected boolean isShowDisabledActions() {
    return false;
  }

  protected boolean isShowNumbers() {
    return false;
  }

  protected String getPopupTitle() {
    return IdeBundle.message("title.popup.new.element");
  }

  public void update(AnActionEvent e){
    final Presentation presentation = e.getPresentation();
    final DataContext context = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(context);
    if (ideView == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(context), e));
  }

  protected ActionGroup getGroup(DataContext dataContext) {
    return (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
  }
}
