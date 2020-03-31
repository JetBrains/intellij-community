// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.json.JsonDialectUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;

public class Json5PsiWalkerFactory implements JsonLikePsiWalkerFactory {
  public static final JsonLikePsiWalker WALKER_INSTANCE = new JsonOriginalPsiWalker() {
    @Override
    public boolean requiresNameQuotes() {
      return false;
    }

    @Override
    public boolean allowsSingleQuotes() {
      return true;
    }
  };

  @Override
  public boolean handles(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    return parent != null && JsonDialectUtil.getLanguageOrDefaultJson(parent) == Json5Language.INSTANCE;
  }

  @NotNull
  @Override
  public JsonLikePsiWalker create(@NotNull JsonSchemaObject schemaObject) {
    return WALKER_INSTANCE;
  }
}
