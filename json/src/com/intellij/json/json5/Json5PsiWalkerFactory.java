// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.json.JsonDialectUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;

public class Json5PsiWalkerFactory implements JsonLikePsiWalkerFactory {
  @Override
  public boolean handles(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent == null) return false;
    return JsonDialectUtil.getLanguage(CompletionUtil.getOriginalOrSelf(parent)) == Json5Language.INSTANCE;
  }

  @NotNull
  @Override
  public JsonLikePsiWalker create(@NotNull JsonSchemaObject schemaObject) {
    return new JsonOriginalPsiWalker() {
      @Override
      public boolean isNameQuoted() {
        return false;
      }

      @Override
      public boolean onlyDoubleQuotesForStringLiterals() {
        return false;
      }
    };
  }
}
