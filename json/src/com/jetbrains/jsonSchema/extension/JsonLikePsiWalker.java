// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Irina.Chernushina on 2/15/2017.
 */
public interface JsonLikePsiWalker {
  JsonOriginalPsiWalker JSON_ORIGINAL_PSI_WALKER = new JsonOriginalPsiWalker();

  boolean isName(PsiElement element);
  boolean isPropertyWithValue(@NotNull PsiElement element);
  PsiElement goUpToCheckable(@NotNull final PsiElement element);
  @Nullable
  List<JsonSchemaVariantsTreeBuilder.Step> findPosition(@NotNull final PsiElement element, boolean forceLastTransition);
  boolean isNameQuoted();
  boolean onlyDoubleQuotesForStringLiterals();
  boolean hasPropertiesBehindAndNoComma(@NotNull PsiElement element);
  Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement element);
  @Nullable
  JsonPropertyAdapter getParentPropertyAdapter(@NotNull PsiElement element);
  boolean isTopJsonElement(@NotNull PsiElement element);
  @Nullable
  JsonValueAdapter createValueAdapter(@NotNull PsiElement element);

  @Nullable
  static JsonLikePsiWalker getWalker(@NotNull final PsiElement element, JsonSchemaObject schemaObject) {
    if (JSON_ORIGINAL_PSI_WALKER.handles(element)) return JSON_ORIGINAL_PSI_WALKER;

    return Arrays.stream(Extensions.getExtensions(JsonLikePsiWalkerFactory.EXTENSION_POINT_NAME))
      .filter(extension -> extension.handles(element))
      .findFirst()
      .map(extension -> extension.create(schemaObject))
      .orElse(null);
  }
}
