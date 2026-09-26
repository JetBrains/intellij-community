// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.json.JsonBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SuggestEnumValuesFix extends PsiUpdateModCommandQuickFix {
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public SuggestEnumValuesFix(JsonLikeSyntaxAdapter quickFixAdapter) {
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JsonBundle.message("replace.with.allowed.value");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
    return getFamilyName();
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement initialElement, @NotNull ModPsiUpdater updater) {
    PsiFile file = initialElement.getContainingFile();
    PsiElement element = myQuickFixAdapter.adjustValue(initialElement);
    final JsonSchemaService jsonSchemaService = JsonSchemaService.Impl.get(project);
    JsonSchemaObject object = jsonSchemaService.getSchemaObject(updater.getOriginalFile(file));
    if (object == null) return;
    List<LookupElement> variants = JsonSchemaCompletionContributor.getCompletionVariants(object, element, element, CompletionType.BASIC);
    if (variants.isEmpty()) return;
    updater.templateBuilder().field(element, new ConstantNode(variants.get(0).getLookupString()).withLookupItems(variants));
  }
}
