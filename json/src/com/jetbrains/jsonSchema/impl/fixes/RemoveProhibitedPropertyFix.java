// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class RemoveProhibitedPropertyFix implements LocalQuickFix {
  @SafeFieldForPreview
  private final JsonValidationError.ProhibitedPropertyIssueData myData;
  @SafeFieldForPreview
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public RemoveProhibitedPropertyFix(JsonValidationError.ProhibitedPropertyIssueData data,
                                     JsonLikeSyntaxAdapter quickFixAdapter) {
    myData = data;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JsonBundle.message("remove.prohibited.property");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
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
