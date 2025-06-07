// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MoveAnnotationOnStaticMemberQualifyingTypeFix extends PsiUpdateModCommandAction<PsiAnnotation> {
  public MoveAnnotationOnStaticMemberQualifyingTypeFix(final @NotNull PsiAnnotation annotation) {
    super(annotation);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaErrorBundle.message("annotation.on.static.member.qualifying.type.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiAnnotation annotation, @NotNull ModPsiUpdater updater) {
    final PsiTypeElement psiTypeElement = getTypeElement(annotation);
    if (psiTypeElement == null) return;

    final PsiJavaCodeReferenceElement innermostParent = psiTypeElement.getInnermostComponentReferenceElement();
    if (innermostParent == null) return;

    final PsiElement rightmostDot = getRightmostDot(innermostParent.getLastChild());
    if (rightmostDot == null) return;

    innermostParent.addAfter(annotation, rightmostDot);

    final CommentTracker ct = new CommentTracker();
    ct.markUnchanged(annotation);
    ct.deleteAndRestoreComments(annotation);
  }

  @Contract(value = "null -> null", pure = true)
  private static @Nullable("The type does not have DOT tokens") PsiElement getRightmostDot(final @Nullable PsiElement element) {
    if (element == null) return null;
    return PsiTreeUtil.findSiblingBackward(element, JavaTokenType.DOT, null);
  }

  /**
   * Returns the type element either from a type parameter or a variable declaration
   *
   * @param startElement element to find the type for
   * @return the type element either from a type parameter or a variable declaration
   */
  @Contract(pure = true)
  private static @Nullable("No type element found") PsiTypeElement getTypeElement(final @NotNull PsiElement startElement) {
    PsiElement parent = PsiTreeUtil.getParentOfType(startElement, PsiTypeElement.class, PsiVariable.class, PsiMethod.class);
    if (parent instanceof PsiTypeElement typeElement) {
      return typeElement;
    }
    if (parent instanceof PsiVariable variable) {
      return variable.getTypeElement();
    }
    if (parent instanceof PsiMethod method) {
      return method.getReturnTypeElement();
    }
    return null;
  }
}
