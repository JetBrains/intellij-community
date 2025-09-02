// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * @deprecated use {@link com.intellij.codeInsight.intention.AddAnnotationModCommandAction#createAddNullableFix(PsiModifierListOwner)} or
 * {@link com.intellij.codeInsight.intention.AddAnnotationModCommandAction#createAddNotNullFix(PsiModifierListOwner)}.
 */
@Deprecated(forRemoval = true)
public class AddNullableNotNullAnnotationFix extends AddAnnotationPsiFix {
  public AddNullableNotNullAnnotationFix(@NotNull String fqn, @NotNull PsiModifierListOwner owner, String @NotNull ... annotationToRemove) {
    super(fqn, owner, annotationToRemove);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (!super.isAvailable(project, psiFile, startElement, endElement)) {
      return false;
    }
    PsiModifierListOwner owner = getContainer(psiFile, startElement.getTextRange().getStartOffset());
    return owner != null &&
           !AnnotationUtil.isAnnotated(owner, getAnnotationsToRemove()[0], CHECK_EXTERNAL | CHECK_TYPE) &&
           isNullabilityAnnotationApplicable(owner);
  }
}
