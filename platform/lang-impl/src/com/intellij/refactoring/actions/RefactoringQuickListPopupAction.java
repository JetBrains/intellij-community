/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.actions;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.Nullable;

public class RefactoringQuickListPopupAction extends QuickSwitchSchemeAction {

  protected void fillActions(@Nullable final Project project,
                             final DefaultActionGroup group,
                             final DataContext dataContext) {
    if (project == null) {
      return;
    }

    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(IdeActions.GROUP_REFACTOR);
    collectEnabledChildren(action, group, dataContext, actionManager);
  }

  private static void collectEnabledChildren(AnAction action,
                                             DefaultActionGroup destinationGroup,
                                             DataContext dataContext,
                                             ActionManager actionManager) {
    if (action instanceof DefaultActionGroup) {
      final AnAction[] children = ((DefaultActionGroup)action).getChildren(null);
      for (AnAction child : children) {
        if (child instanceof DefaultActionGroup) {
          collectEnabledChildren(child, destinationGroup, dataContext, actionManager);
        } else if (child instanceof Separator) {
          destinationGroup.add(child);
        }
        else {
          if (child instanceof BaseRefactoringAction && ((BaseRefactoringAction)child).hasAvailableHandler(dataContext)) {
            final Presentation presentation = new Presentation();
            final AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, presentation, actionManager, 0);
            child.update(event);
            if (presentation.isEnabled() && presentation.isVisible()) {
              destinationGroup.add(child);
            }
          }
        }
      }
    }
  }


  @Override
  protected void showPopup(AnActionEvent e, ListPopup popup) {
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor != null) {
      popup.showInBestPositionFor(editor);
    } else {
      super.showPopup(e, popup);
    }
  }

  protected boolean isEnabled() {
    return true;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(e.getPlace() == ActionPlaces.MAIN_MENU);
  }

  protected String getPopupTitle(AnActionEvent e) {
    return "Refactor This";
  }
}
