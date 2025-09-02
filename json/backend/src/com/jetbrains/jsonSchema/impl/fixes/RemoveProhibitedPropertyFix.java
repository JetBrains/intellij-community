// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.json.JsonBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.impl.JsonValidationError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class RemoveProhibitedPropertyFix extends PsiUpdateModCommandQuickFix {
  private final JsonValidationError.ProhibitedPropertyIssueData myData;
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
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
    if (walker == null) return;
    JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
    assert myData.propertyName.equals(Objects.requireNonNull(parentProperty).getName());
    myQuickFixAdapter.removeProperty(parentProperty.getDelegate());
  }
}
