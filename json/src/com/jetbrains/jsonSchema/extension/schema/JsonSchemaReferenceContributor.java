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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 3/31/2016.
 */
public class JsonSchemaReferenceContributor extends PsiReferenceContributor {
  private static final PsiElementPattern.Capture<JsonValue> REF_PATTERN = createPropertyValuePattern("$ref");

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(REF_PATTERN, new JsonSchemaRefReferenceProvider());
  }

  private static PsiElementPattern.Capture<JsonValue> createPropertyValuePattern(@NotNull final String propertyName) {
    return PlatformPatterns.psiElement(JsonValue.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JsonValue) {
          if (!JsonSchemaFileType.INSTANCE.equals(((PsiElement)element).getContainingFile().getFileType())) return false;
          if (((JsonValue)element).getParent() instanceof JsonProperty &&
              ((JsonProperty)((JsonValue)element).getParent()).getValue() == element) {
            return propertyName.equals(((JsonProperty)((JsonValue)element).getParent()).getName());
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
}
