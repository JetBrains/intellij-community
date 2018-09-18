// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
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
    PsiElement parent = element.getParent();
    return parent != null && (element instanceof JsonElement || element instanceof LeafPsiElement && parent instanceof JsonElement)
           && JsonDialectUtil.isStandardJson(CompletionUtil.getOriginalOrSelf(parent));
  }

  @Override
  public ThreeState isName(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof JsonObject) {
      return ThreeState.YES;
    } else if (parent instanceof JsonProperty) {
      return PsiTreeUtil.isAncestor(((JsonProperty)parent).getNameElement(), element, false) ? ThreeState.YES : ThreeState.NO;
    }
    return ThreeState.NO;
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
  public List<JsonSchemaVariantsTreeBuilder.Step> findPosition(@NotNull PsiElement element, boolean forceLastTransition) {
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
  public Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement originalPosition, PsiElement computedPosition) {
    final JsonObject object = PsiTreeUtil.getParentOfType(originalPosition, JsonObject.class);
    if (object != null) {
      return object.getPropertyList().stream()
        .filter(p -> !isNameQuoted() || p.getNameElement() instanceof JsonStringLiteral)
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

  @Override
  public QuickFixAdapter getQuickFixAdapter(Project project) {
    return new QuickFixAdapter() {
      private final JsonElementGenerator myGenerator = new JsonElementGenerator(project);
      @Nullable
      @Override
      public PsiElement getPropertyValue(PsiElement property) {
        assert property instanceof JsonProperty;
        return ((JsonProperty)property).getValue();
      }

      @NotNull
      @Override
      public String getPropertyName(PsiElement property) {
        assert property instanceof JsonProperty;
        return ((JsonProperty)property).getName();
      }

      @NotNull
      @Override
      public PsiElement createProperty(@NotNull String name, @NotNull String value) {
        return myGenerator.createProperty(name, value);
      }

      @Override
      public boolean ensureComma(PsiElement backward, PsiElement self, PsiElement newElement) {
        if (backward instanceof JsonProperty) {
          self.addAfter(myGenerator.createComma(), backward);
          return true;
        }
        return false;
      }

      @Override
      public void removeIfComma(PsiElement forward) {
        if (forward instanceof LeafPsiElement && ((LeafPsiElement)forward).getElementType() == JsonElementTypes.COMMA) {
          forward.delete();
        }
      }

      @Override
      public boolean fixWhitespaceBefore() {
        return true;
      }
    };
  }
}
