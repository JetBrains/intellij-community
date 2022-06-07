// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

final class RemoveNewKeywordFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  RemoveNewKeywordFix(PsiElement outerParent) {super(outerParent);}

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.remove.new.family.name");
  }

  @Override
  public @NotNull String getText() {
    return JavaAnalysisBundle.message("intention.name.remove.new.display.name");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiNewExpression newDeclaration = tryCast(startElement, PsiNewExpression.class);
    if (newDeclaration == null) return;

    PsiJavaCodeReferenceElement reference = newDeclaration.getClassOrAnonymousClassReference();
    if (reference == null) return;
    PsiElement qualifier = reference.getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return;
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)qualifier;
    CommentTracker ct = new CommentTracker();
    PsiReferenceParameterList parameterList = referenceElement.getParameterList();
    if (parameterList != null) {
      ct.delete(parameterList);
    }

    ct.markRangeUnchanged(reference, Objects.requireNonNullElse(newDeclaration.getArgumentList(), reference));

    String methodCallExpression = newDeclaration.getText().substring(reference.getStartOffsetInParent());
    ct.replaceAndRestoreComments(newDeclaration, methodCallExpression);
  }
}
