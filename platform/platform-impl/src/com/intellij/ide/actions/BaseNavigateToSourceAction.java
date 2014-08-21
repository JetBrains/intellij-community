/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableWithText;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseNavigateToSourceAction extends AnAction implements DumbAware {
  private final boolean myFocusEditor;

  protected BaseNavigateToSourceAction(boolean focusEditor) {
    myFocusEditor = focusEditor;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    OpenSourceUtil.navigate(myFocusEditor, getNavigatables(dataContext));
  }


  public void update(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    final Navigatable target = findTargetForUpdate(dataContext);
    boolean enabled = target != null;
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
      if (!(this instanceof OpenModuleSettingsAction) && OpenModuleSettingsAction.isModuleInProjectViewPopup(event)) {
        event.getPresentation().setVisible(false);
        return;
      }
    }
    else {
      event.getPresentation().setEnabled(enabled);
    }
    //as myFocusEditor is always ignored - Main Menu|View always contains 2 actions with the same name and actually same behaviour
    event.getPresentation().setVisible(target == null || myFocusEditor);
    String navigateActionText = myFocusEditor && target instanceof NavigatableWithText?
                                ((NavigatableWithText)target).getNavigateActionText(true) : null;
    event.getPresentation().setText(navigateActionText == null ? getTemplatePresentation().getText() : navigateActionText);
  }

  @Nullable
  private Navigatable findTargetForUpdate(@NotNull DataContext dataContext) {
    Navigatable[] navigatables = getNavigatables(dataContext);
    if (navigatables == null) return null;

    for (Navigatable navigatable : navigatables) {
      if (navigatable.canNavigate()) {
        return navigatable instanceof PomTargetPsiElement ? ((PomTargetPsiElement)navigatable).getTarget() : navigatable;
      }
    }
    return null;
  }

  @Nullable
  protected Navigatable[] getNavigatables(final DataContext dataContext) {
    return CommonDataKeys.NAVIGATABLE_ARRAY.getData(dataContext);
  }
}
