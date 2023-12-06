// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DeleteMultiCatchFix extends PsiUpdateModCommandAction<PsiTypeElement> {
  public DeleteMultiCatchFix(@NotNull PsiTypeElement typeElement) {
    super(typeElement);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeElement element) {
    return Presentation.of(QuickFixBundle.message("delete.catch.text", JavaHighlightUtil.formatType(element.getType())));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("delete.catch.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeElement element, @NotNull ModPsiUpdater updater) {
    deleteCaughtExceptionType(element);
  }

  public static void deleteCaughtExceptionType(@NotNull PsiTypeElement typeElement) {
    final PsiElement parentType = typeElement.getParent();
    if (!(parentType instanceof PsiTypeElement)) return;

    final PsiElement first;
    final PsiElement last;
    final PsiElement right = PsiTreeUtil.skipWhitespacesAndCommentsForward(typeElement);
    if (PsiUtil.isJavaToken(right, JavaTokenType.OR)) {
      first = typeElement;
      last = right;
    }
    else if (right == null) {
      final PsiElement left = PsiTreeUtil.skipWhitespacesAndCommentsBackward(typeElement);
      if (!(left instanceof PsiJavaToken)) return;
      final IElementType leftType = ((PsiJavaToken)left).getTokenType();
      if (leftType != JavaTokenType.OR) return;
      first = left;
      last = typeElement;
    }
    else {
      return;
    }

    parentType.deleteChildRange(first, last);

    final List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(parentType, PsiTypeElement.class);
    if (typeElements.size() == 1) {
      final PsiElement parameter = parentType.getParent();
      parameter.addRangeAfter(parentType.getFirstChild(), parentType.getLastChild(), parentType);
      parentType.delete();
    }
  }
}
