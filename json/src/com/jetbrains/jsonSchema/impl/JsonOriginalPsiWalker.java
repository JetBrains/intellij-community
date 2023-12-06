// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.adapters.JsonJsonPropertyAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JsonOriginalPsiWalker implements JsonLikePsiWalker {
  public static final JsonOriginalPsiWalker INSTANCE = new JsonOriginalPsiWalker();

  public boolean handles(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    return element instanceof JsonFile && JsonDialectUtil.isStandardJson(element)
           || parent != null && (element instanceof JsonElement || element instanceof LeafPsiElement && parent instanceof JsonElement)
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
    if (element instanceof JsonStringLiteral || element instanceof JsonReferenceExpression) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof JsonProperty) || ((JsonProperty)parent).getNameElement() != element) return false;
      element = parent;
    }
    return element instanceof JsonProperty && ((JsonProperty)element).getValue() != null;
  }

  @Override
  public PsiElement findElementToCheck(@NotNull PsiElement element) {
    PsiElement current = element;
    while (current != null && !(current instanceof PsiFile)) {
      if (current instanceof JsonValue || current instanceof JsonProperty) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  @Override
  public @Nullable JsonPointerPosition findPosition(@NotNull PsiElement element, boolean forceLastTransition) {
    JsonPointerPosition pos = new JsonPointerPosition();
    PsiElement current = element;
    while (! (current instanceof PsiFile)) {
      final PsiElement position = current;
      current = current.getParent();
      if (current instanceof JsonArray array) {
        final List<JsonValue> list = array.getValueList();
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
          final JsonValue value = list.get(i);
          if (value.equals(position)) {
            idx = i;
            break;
          }
        }
        pos.addPrecedingStep(idx);
      } else if (current instanceof JsonProperty) {
        final String propertyName = ((JsonProperty)current).getName();
        current = current.getParent();
        if (!(current instanceof JsonObject)) return null;//incorrect syntax?
        // if either value or not first in the chain - needed for completion variant
        if (position != element || forceLastTransition) {
          pos.addPrecedingStep(propertyName);
        }
      } else if (current instanceof JsonObject && position instanceof JsonProperty) {
        // if either value or not first in the chain - needed for completion variant
        if (position != element || forceLastTransition) {
          final String propertyName = ((JsonProperty)position).getName();
          pos.addPrecedingStep(propertyName);
        }
      } else if (current instanceof PsiFile) {
        break;
      } else {
        return null;//something went wrong
      }
    }
    return pos;
  }

  @Override
  public boolean requiresNameQuotes() {
    return true;
  }

  @Override
  public boolean allowsSingleQuotes() {
    return false;
  }

  @Override
  public boolean hasMissingCommaAfter(@NotNull PsiElement element) {
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
        .filter(p -> !requiresNameQuotes() || p.getNameElement() instanceof JsonStringLiteral)
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

  @Override
  public @Nullable JsonValueAdapter createValueAdapter(@NotNull PsiElement element) {
    return element instanceof JsonValue ? JsonJsonPropertyAdapter.createAdapterByType((JsonValue)element) : null;
  }

  @Override
  public JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) {
    return new JsonLikeSyntaxAdapter() {
      private final JsonElementGenerator myGenerator = new JsonElementGenerator(project);
      @Override
      public @Nullable PsiElement getPropertyValue(PsiElement property) {
        assert property instanceof JsonProperty;
        return ((JsonProperty)property).getValue();
      }

      @Override
      public @NotNull String getPropertyName(PsiElement property) {
        assert property instanceof JsonProperty;
        return ((JsonProperty)property).getName();
      }

      @Override
      public @NotNull PsiElement createProperty(@NotNull String name, @NotNull String value, PsiElement element) {
        return myGenerator.createProperty(name, value);
      }

      @Override
      public boolean ensureComma(PsiElement self, PsiElement newElement) {
        if (newElement instanceof JsonProperty && self instanceof JsonProperty) {
          self.getParent().addAfter(myGenerator.createComma(), self);
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
      public boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element) {
        return true;
      }

      @Override
      public @NotNull String getDefaultValueFromType(@Nullable JsonSchemaType type) {
        return type == null ? "" : type.getDefaultValue();
      }

      @Override
      public PsiElement adjustNewProperty(PsiElement element) {
        return element;
      }

      @Override
      public PsiElement adjustPropertyAnchor(LeafPsiElement element) {
        throw new IncorrectOperationException("Shouldn't use leafs for insertion in pure JSON!");
      }
    };
  }

  @Override
  public @Nullable PsiElement getParentContainer(PsiElement element) {
    return PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, JsonProperty.class),
                                JsonObject.class, JsonArray.class);
  }

  @Override
  public @NotNull Collection<PsiElement> getRoots(@NotNull PsiFile file) {
    return file instanceof JsonFile ? ContainerUtil.createMaybeSingletonList(((JsonFile)file).getTopLevelValue()) : ContainerUtil.emptyList();
  }

  @Override
  public @Nullable PsiElement getPropertyNameElement(PsiElement property) {
    return property instanceof JsonProperty ? ((JsonProperty)property).getNameElement() : null;
  }

  @Override
  public TextRange adjustErrorHighlightingRange(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof JsonFile) {
      PsiElement child = PsiTreeUtil.skipMatching(element.getFirstChild(), e -> e.getNextSibling(), e -> !(e instanceof JsonElement));
      return child == null ? element.getTextRange() : child.getTextRange();
    }
    return element.getTextRange();
  }
}
