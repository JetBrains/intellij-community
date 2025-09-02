// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.json.psi.*;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonSchemaReferenceContributor extends PsiReferenceContributor {
  private static final class Holder {
    private static final PsiElementPattern.Capture<JsonValue> REF_PATTERN = createPropertyValuePattern("$ref", true, false);
    private static final PsiElementPattern.Capture<JsonValue> REC_REF_PATTERN = createPropertyValuePattern("$recursiveRef", true, false);
    private static final PsiElementPattern.Capture<JsonValue> SCHEMA_PATTERN = createPropertyValuePattern("$schema", false, true);
    private static final PsiElementPattern.Capture<JsonStringLiteral> REQUIRED_PROP_PATTERN = createRequiredPropPattern();
  }
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(Holder.REF_PATTERN, new JsonPointerReferenceProvider(false));
    registrar.registerReferenceProvider(Holder.REC_REF_PATTERN, new JsonPointerReferenceProvider(false));
    registrar.registerReferenceProvider(Holder.SCHEMA_PATTERN, new JsonPointerReferenceProvider(true));
    registrar.registerReferenceProvider(Holder.REQUIRED_PROP_PATTERN, new JsonRequiredPropsReferenceProvider());
  }

  private static PsiElementPattern.Capture<JsonValue> createPropertyValuePattern(
    @SuppressWarnings("SameParameterValue") final @NotNull String propertyName, boolean schemaOnly, boolean rootOnly) {

    return PlatformPatterns.psiElement(JsonValue.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JsonValue) {
          JsonProperty property = ObjectUtils.tryCast(((JsonValue)element).getParent(), JsonProperty.class);
          if (property != null && property.getValue() == element && propertyName.equals(property.getName())) {
            final PsiFile file = property.getContainingFile();
            if (rootOnly && (!(file instanceof JsonFile) || ((JsonFile)file).getTopLevelValue() != property.getParent())) return false;
            if (schemaOnly && !JsonSchemaService.isSchemaFile(CompletionUtil.getOriginalOrSelf(file))) return false;
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  }

  private static PsiElementPattern.Capture<JsonStringLiteral> createRequiredPropPattern() {
    return PlatformPatterns.psiElement(JsonStringLiteral.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (!(element instanceof JsonStringLiteral)) return false;
        final PsiElement parent = ((JsonStringLiteral)element).getParent();
        if (!(parent instanceof JsonArray)) return false;
        PsiElement property = parent.getParent();
        if (!(property instanceof JsonProperty)) return false;
        return "required".equals(((JsonProperty)property).getName()) &&
               JsonSchemaService.isSchemaFile(((JsonStringLiteral)element).getContainingFile());
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  }
}
