// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.numeric;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Converts content of string literals to proposed values. The sole purpose of the class is to act as quickfix
 * for adding/removing underscores to/from numeric literals.
 *
 * @see InsertLiteralUnderscoresInspection
 * @see RemoveLiteralUnderscoresInspection
 */
final class ConvertNumericLiteralQuickFix implements LocalQuickFix {

  @NotNull private final String myConvertedValue;
  @NotNull @IntentionName private final String myName;
  @NotNull @IntentionFamilyName private final String myFamilyName;

  ConvertNumericLiteralQuickFix(@NotNull final String convertedValue,
                                @NotNull @IntentionName final String name,
                                @NotNull @IntentionFamilyName String familyName) {
    myConvertedValue = convertedValue;
    myName = name;
    myFamilyName = familyName;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return myFamilyName;
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();

    if (element == null) return;

    final PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myConvertedValue, null);
    element.replace(replacement);
  }
}
