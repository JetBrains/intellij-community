// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableWithText;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseNavigateToSourceAction extends DumbAwareAction {
  private final boolean myFocusEditor;

  protected BaseNavigateToSourceAction(boolean focusEditor) {
    myFocusEditor = focusEditor;
    setInjectedContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    OpenSourceUtil.navigate(myFocusEditor, getNavigatables(dataContext));
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean inPopup = ActionPlaces.isPopupPlace(e.getPlace());
    Navigatable target = findTargetForUpdate(e.getDataContext());
    boolean enabled = target != null;
    if (inPopup && !(this instanceof OpenModuleSettingsAction) && OpenModuleSettingsAction.isModuleInProjectViewPopup(e)) {
      e.getPresentation().setVisible(false);
      return;
    }
    //as myFocusEditor is always ignored - Main Menu|View always contains 2 actions with the same name and actually same behaviour
    e.getPresentation().setVisible((enabled || !inPopup) &&
                                   (myFocusEditor || !(target instanceof NavigatableWithText)));
    e.getPresentation().setEnabled(enabled);

    String navigateActionText = myFocusEditor && target instanceof NavigatableWithText ?
                                ((NavigatableWithText)target).getNavigateActionText(true) : null;
    if (navigateActionText != null) {
      e.getPresentation().setText(navigateActionText);
    }
    else {
      e.getPresentation().setTextWithMnemonic(getTemplatePresentation().getTextWithPossibleMnemonic());
    }
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

  protected Navigatable @Nullable [] getNavigatables(final DataContext dataContext) {
    return CommonDataKeys.NAVIGATABLE_ARRAY.getData(dataContext);
  }
}
