/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 4/15/2016.
 */
public class JsonBySchemaObjectReferenceContributor extends PsiReferenceContributor {
  public static final PsiElementPattern.Capture<JsonStringLiteral> REF_PATTERN = createPropertyNamePattern();

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(REF_PATTERN, new JsonPropertyName2SchemaDefinitionReferenceProvider());
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
}
