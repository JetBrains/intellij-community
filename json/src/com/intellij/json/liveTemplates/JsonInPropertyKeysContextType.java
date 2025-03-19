// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.liveTemplates;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonValue;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public final class JsonInPropertyKeysContextType extends TemplateContextType {
  private JsonInPropertyKeysContextType() {
    super(JsonBundle.message("json.property.keys"));
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file instanceof JsonFile && psiElement().inside(psiElement(JsonValue.class)
                                                             .with(new PatternCondition<PsiElement>("insidePropertyKey") {
                                                               @Override
                                                               public boolean accepts(@NotNull PsiElement element,
                                                                                      ProcessingContext context) {
                                                                 return JsonPsiUtil.isPropertyKey(element);
                                                               }
                                                             })).beforeLeaf(psiElement(JsonElementTypes.COLON)).accepts(file.findElementAt(offset));
  }
}