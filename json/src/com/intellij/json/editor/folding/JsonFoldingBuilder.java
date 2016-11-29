package com.intellij.json.editor.folding;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
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

/**
 * @author Mikhail Golubev
 */
public class JsonFoldingBuilder implements FoldingBuilder, DumbAware {
  @NotNull
  @Override
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    final List<FoldingDescriptor> descriptors = new ArrayList<>();
    collectDescriptorsRecursively(node, document, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
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

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == JsonElementTypes.OBJECT) {
      final JsonObject object = node.getPsi(JsonObject.class);
      final List<JsonProperty> properties = object.getPropertyList();
      JsonProperty candidate = null;
      for (JsonProperty property : properties) {
        final String name = property.getName();
        final JsonValue value = property.getValue();
        if (value instanceof JsonLiteral) {
          if ("id".equals(name) || "name".equals(name)) {
            candidate = property;
            break;
          }
          if (candidate == null) {
            candidate = property;
          }
        }
      }
      if (candidate != null) {
        //noinspection ConstantConditions
        return "{\"" + candidate.getName() + "\": " + candidate.getValue().getText() + "...}";
      }
      return "{...}";
    }
    else if (type == JsonElementTypes.ARRAY) {
      return "[...]";
    }
    else if (type == JsonElementTypes.LINE_COMMENT) {
      return "//...";
    }
    else if (type == JsonElementTypes.BLOCK_COMMENT) {
      return "/*...*/";
    }
    return "...";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  @NotNull
  public static Couple<PsiElement> expandLineCommentsRange(@NotNull PsiElement anchor) {
    return Couple.of(JsonPsiUtil.findFurthestSiblingOfSameType(anchor, false), JsonPsiUtil.findFurthestSiblingOfSameType(anchor, true));
  }

  private static boolean spanMultipleLines(@NotNull ASTNode node, @NotNull Document document) {
    final TextRange range = node.getTextRange();
    return document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset());
  }
}
