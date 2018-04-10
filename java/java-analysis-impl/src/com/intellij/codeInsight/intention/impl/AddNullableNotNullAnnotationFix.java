// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * @deprecated use {@link AddAnnotationPsiFix#createAddNullableFix(PsiModifierListOwner)} or
 * {@link AddAnnotationPsiFix#createAddNotNullFix(PsiModifierListOwner)}.
 */
@Deprecated
public class AddNullableNotNullAnnotationFix extends AddAnnotationPsiFix {
  public AddNullableNotNullAnnotationFix(@NotNull String fqn, @NotNull PsiModifierListOwner owner, @NotNull String... annotationToRemove) {
    super(fqn, owner, PsiNameValuePair.EMPTY_ARRAY, annotationToRemove);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (!super.isAvailable(project, file, startElement, endElement)) {
      return false;
    }
    PsiModifierListOwner owner = getContainer(file, startElement.getTextRange().getStartOffset());
    return owner != null &&
           !AnnotationUtil.isAnnotated(owner, getAnnotationsToRemove()[0], CHECK_EXTERNAL | CHECK_TYPE) &&
           isNullabilityAnnotationApplicable(owner);
  }
}
