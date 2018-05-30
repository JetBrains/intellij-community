// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RemoveProhibitedPropertyFix implements LocalQuickFix {
  private final JsonValidationError.ProhibitedPropertyIssueData myData;

  public RemoveProhibitedPropertyFix(JsonValidationError.ProhibitedPropertyIssueData data) {
    myData = data;
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
    if (!(element instanceof JsonProperty)) return;
    assert myData.propertyName.equals(((JsonProperty)element).getName());
    PsiElement forward = PsiTreeUtil.skipWhitespacesForward(element);
    element.delete();
    if (forward instanceof LeafPsiElement && ((LeafPsiElement)forward).getElementType() == JsonElementTypes.COMMA) {
      forward.delete();
    }
  }
}
