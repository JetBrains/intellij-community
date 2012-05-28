package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * @author oleg
 */
public abstract class IndentationFoldingBuilder implements FoldingBuilder, DumbAware {
  private final TokenSet myTokenSet;

  public IndentationFoldingBuilder(final TokenSet tokenSet) {
    myTokenSet = tokenSet;
  }

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode astNode, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new LinkedList<FoldingDescriptor>();
    collectDescriptors(astNode, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void collectDescriptors(@NotNull final ASTNode node, @NotNull final List<FoldingDescriptor> descriptors) {
    final ASTNode[] children = node.getChildren(myTokenSet);
    if (children.length > 0) {
      if (node.getTreeParent() !=null && node.getTextLength() > 1) {
        descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      }
      for (ASTNode child : children) {
        collectDescriptors(child, descriptors);
      }
    }
  }

  @Nullable
  public String getPlaceholderText(@NotNull final ASTNode node) {
    final StringBuilder builder = new StringBuilder();
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      String text = child.getText();
      if (text == null) {
        if (builder.length() > 0) {
          break;
        }
      }
      else if (!text.contains("\n")) {
        builder.append(text);
      }
      else if (builder.length() > 0) {
        builder.append(text.substring(0, text.indexOf('\n')));
        break;
      }
      else {
        builder.append(getFirstNonEmptyLine(text));
        if (builder.length() > 0) {
          break;
        }
      }
      child = child.getTreeNext();
    }
    return builder.toString();
  }

  @NotNull
  private static String getFirstNonEmptyLine(@NotNull final String text) {
    int start = 0;
    int end;
    while ((end = text.indexOf('\n', start)) != -1 && start >= end) {
      start = end + 1;
    }
    return end == -1 ? text.substring(start) : text.substring(start, end);
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
