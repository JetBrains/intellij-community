// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MoveAnnotationOnStaticMemberQualifyingTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  MoveAnnotationOnStaticMemberQualifyingTypeFix(@NotNull final PsiAnnotation annotation) {
    super(annotation);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaErrorBundle.message("annotation.on.static.member.qualifying.type.family.name");
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull final PsiFile file,
                     @Nullable final Editor editor,
                     @NotNull final PsiElement startElement,
                     @NotNull final PsiElement endElement) {
    final PsiTypeElement psiTypeElement = getTypeElement(startElement);
    if (psiTypeElement == null) return;

    final PsiJavaCodeReferenceElement innermostParent = psiTypeElement.getInnermostComponentReferenceElement();
    if (innermostParent == null) return;

    final PsiElement rightmostDot = getRightmostDot(innermostParent.getLastChild());
    if (rightmostDot == null) return;

    innermostParent.addAfter(startElement, rightmostDot);

    final CommentTracker ct = new CommentTracker();
    ct.markUnchanged(startElement);
    ct.deleteAndRestoreComments(startElement);
  }

  @Nullable("The type does not have DOT tokens")
  @Contract(value = "null -> null", pure = true)
  private static PsiElement getRightmostDot(@Nullable final PsiElement element) {
    if (element == null) return null;
    return PsiTreeUtil.findSiblingBackward(element, JavaTokenType.DOT, null);
  }

  /**
   * Returns the type element either from a type parameter or a variable declaration
   *
   * @param startElement element to find the type for
   * @return the type element either from a type parameter or a variable declaration
   */
  @Nullable("No type element found")
  @Contract(pure = true)
  private static PsiTypeElement getTypeElement(@NotNull final PsiElement startElement) {
    final PsiTypeElement psiTypeElement = PsiTreeUtil.getParentOfType(startElement, PsiTypeElement.class);
    if (psiTypeElement != null) return psiTypeElement;

    final PsiVariable variable = PsiTreeUtil.getParentOfType(startElement, PsiVariable.class);
    if (variable == null) return null;

    return variable.getTypeElement();
  }
}
