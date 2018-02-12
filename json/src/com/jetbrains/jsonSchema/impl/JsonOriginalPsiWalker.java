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

import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.adapters.JsonJsonPropertyAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 2/16/2017.
 */
public class JsonOriginalPsiWalker implements JsonLikePsiWalker {
  public static final JsonOriginalPsiWalker INSTANCE = new JsonOriginalPsiWalker();

  public boolean handles(@NotNull PsiElement element) {
    return element instanceof JsonElement || element instanceof LeafPsiElement && element.getParent() instanceof JsonElement;
  }

  @Override
  public boolean isName(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof JsonObject) {
      return true;
    } else if (parent instanceof JsonProperty) {
      return PsiTreeUtil.isAncestor(((JsonProperty)parent).getNameElement(), element, false);
    }
    return false;
  }

  @Override
  public boolean isPropertyWithValue(@NotNull PsiElement element) {
    return element instanceof JsonProperty && ((JsonProperty)element).getValue() != null;
  }

  @Override
  public PsiElement goUpToCheckable(@NotNull PsiElement element) {
    PsiElement current = element;
    while (current != null && !(current instanceof PsiFile)) {
      if (current instanceof JsonValue || current instanceof JsonProperty) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  @Nullable
  @Override
  public List<JsonSchemaVariantsTreeBuilder.Step> findPosition(@NotNull PsiElement element, boolean isName, boolean forceLastTransition) {
    final List<JsonSchemaVariantsTreeBuilder.Step> steps = new ArrayList<>();
    PsiElement current = element;
    while (! (current instanceof PsiFile)) {
      final PsiElement position = current;
      current = current.getParent();
      if (current instanceof JsonArray) {
        JsonArray array = (JsonArray)current;
        final List<JsonValue> list = array.getValueList();
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
          final JsonValue value = list.get(i);
          if (value.equals(position)) {
            idx = i;
            break;
          }
        }
        steps.add(JsonSchemaVariantsTreeBuilder.Step.createArrayElementStep(idx));
      } else if (current instanceof JsonProperty) {
        final String propertyName = ((JsonProperty)current).getName();
        current = current.getParent();
        if (!(current instanceof JsonObject)) return null;//incorrect syntax?
        // if either value or not first in the chain - needed for completion variant
        if (position != element || forceLastTransition) {
          steps.add(JsonSchemaVariantsTreeBuilder.Step.createPropertyStep(propertyName));
        }
      } else if (current instanceof JsonObject && position instanceof JsonProperty) {
        // if either value or not first in the chain - needed for completion variant
        if (position != element || forceLastTransition) {
          final String propertyName = ((JsonProperty)position).getName();
          steps.add(JsonSchemaVariantsTreeBuilder.Step.createPropertyStep(propertyName));
        }
      } else if (current instanceof PsiFile) {
        break;
      } else {
        return null;//something went wrong
      }
    }
    Collections.reverse(steps);
    return steps;
  }

  @Override
  public boolean isNameQuoted() {
    return true;
  }

  @Override
  public boolean onlyDoubleQuotesForStringLiterals() {
    return true;
  }

  @Override
  public boolean hasPropertiesBehindAndNoComma(@NotNull PsiElement element) {
    PsiElement current = element instanceof JsonProperty ? element : PsiTreeUtil.getParentOfType(element, JsonProperty.class);
    while (current != null && current.getNode().getElementType() != JsonElementTypes.COMMA) {
      current = current.getNextSibling();
    }
    int commaOffset = current == null ? Integer.MAX_VALUE : current.getTextRange().getStartOffset();
    final int offset = element.getTextRange().getStartOffset();
    final JsonObject object = PsiTreeUtil.getParentOfType(element, JsonObject.class);
    if (object != null) {
      for (JsonProperty property : object.getPropertyList()) {
        final int pOffset = property.getTextRange().getStartOffset();
        if (pOffset >= offset && !PsiTreeUtil.isAncestor(property, element, false)) {
          return pOffset < commaOffset;
        }
      }
    }
    return false;
  }

  @Override
  public Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement element) {
    final JsonObject object = PsiTreeUtil.getParentOfType(element, JsonObject.class);
    if (object != null) {
      return object.getPropertyList().stream()
        .filter(p -> p.getNameElement() instanceof JsonStringLiteral)
        .map(p -> StringUtil.unquoteString(p.getName())).collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }

  @Override
  public JsonPropertyAdapter getParentPropertyAdapter(@NotNull PsiElement element) {
    final JsonProperty property = PsiTreeUtil.getParentOfType(element, JsonProperty.class, false);
    if (property == null) return null;
    return new JsonJsonPropertyAdapter(property);
  }

  @Override
  public boolean isTopJsonElement(@NotNull PsiElement element) {
    return element instanceof PsiFile;
  }

  @Nullable
  @Override
  public JsonValueAdapter createValueAdapter(@NotNull PsiElement element) {
    return element instanceof JsonValue ? JsonJsonPropertyAdapter.createAdapterByType((JsonValue)element) : null;
  }
}
