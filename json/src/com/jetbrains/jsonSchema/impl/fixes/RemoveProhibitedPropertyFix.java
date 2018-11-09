// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RemoveProhibitedPropertyFix implements LocalQuickFix {
  private final JsonValidationError.ProhibitedPropertyIssueData myData;
  private final JsonLikePsiWalker.QuickFixAdapter myQuickFixAdapter;

  public RemoveProhibitedPropertyFix(JsonValidationError.ProhibitedPropertyIssueData data,
                                     JsonLikePsiWalker.QuickFixAdapter quickFixAdapter) {
    myData = data;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Remove prohibited property";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return getFamilyName() + " '" + myData.propertyName + "'";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    assert myData.propertyName.equals(myQuickFixAdapter.getPropertyName(element));
    PsiElement forward = PsiTreeUtil.skipWhitespacesForward(element);
    element.delete();
    myQuickFixAdapter.removeIfComma(forward);
  }
}
