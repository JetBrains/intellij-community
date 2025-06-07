// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TestCompilerReferenceServiceAction extends AnAction {
  public TestCompilerReferenceServiceAction(@NlsActions.ActionText String text) {
    super(text);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    final PsiElement element = getPsiElement(e.getDataContext());
    if (element != null) startActionFor(element);
  }

  protected abstract void startActionFor(@NotNull PsiElement element);

  protected abstract boolean canBeAppliedFor(@NotNull PsiElement element);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    if (!CompilerReferenceServiceBase.isEnabled() ||
        !Registry.is("enable.compiler.reference.index.test.actions")) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(getPsiElement(e.getDataContext()) != null);
  }

  private @Nullable PsiElement getPsiElement(DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return null;
    final PsiElement element = TargetElementUtil.getInstance().findTargetElement(editor,
                                                                                 TargetElementUtil.ELEMENT_NAME_ACCEPTED,
                                                                                 editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }
    return canBeAppliedFor(element) ? element : null;
  }
}
