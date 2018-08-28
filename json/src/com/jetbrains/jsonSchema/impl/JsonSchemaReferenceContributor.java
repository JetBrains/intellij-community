/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 3/31/2016.
 */
public class JsonSchemaReferenceContributor extends PsiReferenceContributor {
  private static final PsiElementPattern.Capture<JsonValue> REF_PATTERN = createPropertyValuePattern("$ref");
  public static final PsiElementPattern.Capture<JsonStringLiteral> PROPERTY_NAME_PATTERN = createPropertyNamePattern();
  public static final PsiElementPattern.Capture<JsonStringLiteral> REQUIRED_PROP_PATTERN = createRequiredPropPattern();

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(REF_PATTERN, new JsonSchemaRefReferenceProvider());
    registrar.registerReferenceProvider(PROPERTY_NAME_PATTERN, new JsonPropertyName2SchemaDefinitionReferenceProvider());
    registrar.registerReferenceProvider(REQUIRED_PROP_PATTERN, new JsonRequiredPropsReferenceProvider());
  }

  private static PsiElementPattern.Capture<JsonValue> createPropertyValuePattern(
    @SuppressWarnings("SameParameterValue") @NotNull final String propertyName) {

    return PlatformPatterns.psiElement(JsonValue.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JsonValue) {
          final JsonValue value = (JsonValue) element;
          if (!JsonSchemaService.isSchemaFile(value.getContainingFile())) return false;

          if (value.getParent() instanceof JsonProperty && ((JsonProperty)value.getParent()).getValue() == element) {
            return propertyName.equals(((JsonProperty)value.getParent()).getName());
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

  private static PsiElementPattern.Capture<JsonStringLiteral> createPropertyNamePattern() {
    return PlatformPatterns.psiElement(JsonStringLiteral.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JsonStringLiteral) {
          final PsiElement parent = ((JsonStringLiteral)element).getParent();
          return parent instanceof JsonProperty && ((JsonProperty)parent).getNameElement() == element;
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
        if (!JsonSchemaService.isSchemaFile(((JsonStringLiteral)element).getContainingFile())) return false;
        final PsiElement parent = ((JsonStringLiteral)element).getParent();
        if (!(parent instanceof JsonArray)) return false;
        PsiElement property = parent.getParent();
        if (!(property instanceof JsonProperty)) return false;
        return "required".equals(((JsonProperty)property).getName());
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  }
}
