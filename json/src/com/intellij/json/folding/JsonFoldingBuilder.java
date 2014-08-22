package com.intellij.json.folding;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
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
    final List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
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
    for (ASTNode child : node.getChildren(null)) {
      collectDescriptorsRecursively(child, document, descriptors);
    }
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == JsonElementTypes.OBJECT) {
      return "{...}";
    }
    else if (type == JsonElementTypes.ARRAY) {
      return "[...]";
    }
    return "...";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  private static boolean spanMultipleLines(@NotNull ASTNode node, @NotNull Document document) {
    final TextRange range = node.getTextRange();
    return document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset());
  }
}
