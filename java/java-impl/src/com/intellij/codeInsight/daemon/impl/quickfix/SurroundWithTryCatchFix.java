// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SurroundWithTryCatchFix implements ModCommandAction {
  private final PsiElement myElement;

  public SurroundWithTryCatchFix(@NotNull PsiElement element) {
    if (element instanceof PsiExpression &&
        PsiTreeUtil.getParentOfType(element, PsiResourceListElement.class, false, PsiStatement.class) != null) {
      // We are inside resource list: there's already a suggestion to add a catch to this try.
      // Suggesting wrapping with another try-catch is confusing
      myElement = null;
      return;
    }
    if (element instanceof PsiStatement ||
        (element instanceof PsiExpression expression &&
         !(element instanceof PsiMethodReferenceExpression) &&
         CodeBlockSurrounder.canSurround(ExpressionUtils.getTopLevelExpression(expression)))) {
      myElement = element;
    } else {
      myElement = null;
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("surround.with.try.catch.fix");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return myElement != null && myElement.isValid() ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(myElement, (element, updater) -> invoke(context.project(), element, updater));
  }

  private static void invoke(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PsiExpression expression) {
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(ExpressionUtils.getTopLevelExpression(expression));
      if (surrounder == null) return;
      element = surrounder.surround().getAnchor();
    } else {
      element = CommonJavaRefactoringUtil.getParentStatement(element, false);
      if (element == null) return;
    }

    TextRange range = null;

    try{
      JavaWithTryCatchSurrounder handler = new JavaWithTryCatchSurrounder();
      range = handler.doSurround(project, element.getParent(), new PsiElement[]{element});
    }
    catch(IncorrectOperationException e){
      updater.cancel(JavaBundle.message("surround.with.try.catch.incorrect.template.message"));
    }
    if (range != null) {
      updater.select(range);
    }
  }
}
