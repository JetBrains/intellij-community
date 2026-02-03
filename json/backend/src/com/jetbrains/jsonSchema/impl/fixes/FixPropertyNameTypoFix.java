// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.json.JsonBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class FixPropertyNameTypoFix extends PsiUpdateModCommandQuickFix {
  private final String myAltName;
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public FixPropertyNameTypoFix(String altName,
                                     JsonLikeSyntaxAdapter quickFixAdapter) {
    myAltName = altName;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JsonBundle.message("fix.property.name.spelling", myAltName);
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
    if (walker == null) return;
    JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
    if (parentProperty == null) return;
    PsiElement newProperty = walker.getSyntaxAdapter(project).createProperty(myAltName, "foo", project);
    parentProperty.getNameValueAdapter().getDelegate().replace(
      walker.getParentPropertyAdapter(newProperty).getNameValueAdapter().getDelegate()
    );
  }
}
