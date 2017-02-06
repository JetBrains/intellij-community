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

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.jetbrains.jsonSchema.extension.schema.JsonSchemaBaseReference;
import com.jetbrains.jsonSchema.extension.schema.JsonSchemaInsideSchemaResolver;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.jsonSchema.extension.schema.JsonSchemaInsideSchemaResolver.PROPERTIES;

/**
 * @author Irina.Chernushina on 4/15/2016.
 */
public class JsonPropertyName2SchemaDefinitionReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return new PsiReference[] {new JsonPropertyName2SchemaRefReference((JsonStringLiteral)element)};
  }

  private static class JsonPropertyName2SchemaRefReference extends JsonSchemaBaseReference<JsonStringLiteral> {
    public JsonPropertyName2SchemaRefReference(JsonStringLiteral element) {
      super(element, ElementManipulators.getValueTextRange(element));
    }

    @Nullable
    @Override
    public PsiElement resolveInner() {
      final String reference = getReference();
      if (reference == null) return null;
      final JsonSchemaServiceEx schemaServiceEx = JsonSchemaService.Impl.getEx(myElement.getProject());
      final VirtualFile file = myElement.getContainingFile().getVirtualFile();
      if (file == null) return null;
      final Collection<Pair<VirtualFile, String>> pairs = schemaServiceEx.getSchemaFilesByFile(file);
      if (pairs != null && ! pairs.isEmpty()) {
        for (Pair<VirtualFile, String> pair : pairs) {
          final VirtualFile schemaFile = pair.getFirst();
          final List<JsonSchemaWalker.Step> steps = JsonSchemaWalker.findPosition(getElement(), true, true);
          if (steps == null) continue;
          final PsiElement element =
            new JsonSchemaInsideSchemaResolver(myElement.getProject(), schemaFile, reference, steps).resolveInSchemaRecursively();
          if (element != null) return element;
        }
      }

      return null;
    }

    private String getReference() {
      final List<String> names = new ArrayList<>();
      final PsiElement parent = getElement().getParent();
      if (!(parent instanceof JsonProperty)) return null;
      JsonProperty element = (JsonProperty)parent;
      while (true) {
        names.add(StringUtil.unquoteString(element.getName()));
        if (!(element.getParent() instanceof JsonObject)) break;
        final PsiElement grand = element.getParent().getParent();
        //noinspection ConstantConditions
        if (grand instanceof JsonProperty && ((JsonProperty)grand).getValue() != null && ((JsonProperty)grand).getValue().equals(element.getParent())) {
          element = (JsonProperty)grand;
        }
        else break;
      }
      final StringBuilder path = new StringBuilder();
      Collections.reverse(names);
      for (String name : names) {
        path.append(PROPERTIES).append(name);
      }
      return path.toString();
    }
  }
}
