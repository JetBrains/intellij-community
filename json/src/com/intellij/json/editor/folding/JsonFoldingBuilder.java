// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor.folding;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
import com.intellij.json.psi.impl.JsonCollectionPsiPresentationUtils;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
public final class JsonFoldingBuilder implements FoldingBuilder, DumbAware {
  private static final Set<String> PRIORITIZED_KEYS = Set.of("id", "name");

  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    final List<FoldingDescriptor> descriptors = new ArrayList<>();
    collectDescriptorsRecursively(node, document, descriptors);
    return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  private static void collectDescriptorsRecursively(@NotNull ASTNode node,
                                                    @NotNull Document document,
                                                    @NotNull List<FoldingDescriptor> descriptors) {
    final IElementType type = node.getElementType();
    if ((type == JsonElementTypes.OBJECT || type == JsonElementTypes.ARRAY) && spanMultipleLines(node, document)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
    else if (type == JsonElementTypes.BLOCK_COMMENT) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
    else if (type == JsonElementTypes.LINE_COMMENT) {
      final Couple<PsiElement> commentRange = expandLineCommentsRange(node.getPsi());
      final int startOffset = commentRange.getFirst().getTextRange().getStartOffset();
      final int endOffset = commentRange.getSecond().getTextRange().getEndOffset();
      if (document.getLineNumber(startOffset) != document.getLineNumber(endOffset)) {
        descriptors.add(new FoldingDescriptor(node, new TextRange(startOffset, endOffset)));
      }
    }

    for (ASTNode child : node.getChildren(null)) {
      collectDescriptorsRecursively(child, document, descriptors);
    }
  }

  @Override
  public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == JsonElementTypes.OBJECT) {
      return buildObjectPlaceholder(node.getPsi(JsonObject.class));
    }
    else if (type == JsonElementTypes.ARRAY && node.getPsi() instanceof JsonArray arrayNode) {
      return JsonCollectionPsiPresentationUtils.getCollectionPsiPresentationText(arrayNode);
    }
    else if (type == JsonElementTypes.LINE_COMMENT) {
      return "//...";
    }
    else if (type == JsonElementTypes.BLOCK_COMMENT) {
      return "/*...*/";
    }
    return "...";
  }

  private static String buildObjectPlaceholder(JsonObject object) {
    List<JsonProperty> properties = object.getPropertyList();
    JsonFoldingSettings settings = JsonFoldingSettings.getInstance();
    if (settings.showKeyCount) {
      return JsonBundle.message("folding.collapsed.object.text", properties.size());
    }
    JsonProperty candidate = chooseCandidateProperty(properties, settings);
    return candidate != null
           ? "{\"" + candidate.getName() + "\": " + Objects.requireNonNull(candidate.getValue()).getText() + "...}"
           : JsonCollectionPsiPresentationUtils.getCollectionPsiPresentationText(properties.size());
  }

  private static @Nullable JsonProperty chooseCandidateProperty(List<JsonProperty> properties, JsonFoldingSettings settings) {
    JsonProperty candidate = null;
    for (JsonProperty property : properties) {
      if (!(property.getValue() instanceof JsonLiteral)) continue;
      if (!settings.showFirstKey && PRIORITIZED_KEYS.contains(property.getName())) {
        return property;
      }
      if (candidate == null) {
        candidate = property;
      }
    }
    return candidate;
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  public static @NotNull Couple<PsiElement> expandLineCommentsRange(@NotNull PsiElement anchor) {
    return Couple.of(JsonPsiUtil.findFurthestSiblingOfSameType(anchor, false), JsonPsiUtil.findFurthestSiblingOfSameType(anchor, true));
  }

  private static boolean spanMultipleLines(@NotNull ASTNode node, @NotNull Document document) {
    final TextRange range = node.getTextRange();
    int endOffset = range.getEndOffset();
    return document.getLineNumber(range.getStartOffset())
           < (endOffset < document.getTextLength() ? document.getLineNumber(endOffset) : document.getLineCount() - 1);
  }
}
