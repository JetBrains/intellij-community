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
      if (node.getTreeParent() !=null) {
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
    String text;
    while (child != null && (text = child.getText()) != null &&  !text.contains("\n")){
      builder.append(text);
      child = child.getTreeNext();
    }
    return builder.toString();
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
