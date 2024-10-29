// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TreeBackedLighterAST extends LighterAST {
  private final FileASTNode myRoot;

  public TreeBackedLighterAST(@NotNull FileASTNode root) {
    super(root.getCharTable());
    myRoot = root;
  }

  @Override
  public @NotNull LighterASTNode getRoot() {
    return wrap(myRoot);
  }

  @Override
  public LighterASTNode getParent(final @NotNull LighterASTNode node) {
    ASTNode astNode = unwrap(node);
    ASTNode parent = astNode.getTreeParent();
    return parent == null ? null : wrap(parent);
  }

  @Override
  public @NotNull List<LighterASTNode> getChildren(final @NotNull LighterASTNode parent) {
    final ASTNode[] children = unwrap(parent).getChildren(null);
    if (children.length == 0) return ContainerUtil.emptyList();

    List<LighterASTNode> result = new ArrayList<>(children.length);
    for (final ASTNode child : children) {
      result.add(wrap(child));
    }
    return result;
  }

  public static @NotNull LighterASTNode wrap(@NotNull ASTNode node) {
    if (node instanceof LighterASTNode) return (LighterASTNode)node;
    return node.getFirstChildNode() == null && node.getTextLength() > 0 ? new TokenNodeWrapper(node) : new NodeWrapper(node);
  }

  public @NotNull ASTNode unwrap(@NotNull LighterASTNode node) {
    if (node instanceof ASTNode) return (ASTNode)node;
    return ((NodeWrapper)node).myNode;
  }

  private static class NodeWrapper implements LighterASTNode {
    protected final ASTNode myNode;

    NodeWrapper(ASTNode node) {
      myNode = node;
    }

    @Override
    public @NotNull IElementType getTokenType() {
      return myNode.getElementType();
    }

    @Override
    public int getStartOffset() {
      return myNode.getStartOffset();
    }

    @Override
    public int getEndOffset() {
      return myNode.getStartOffset() + myNode.getTextLength();
    }

    @Override
    public int getTextLength() {
      return myNode.getTextLength();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof NodeWrapper)) return false;
      final NodeWrapper that = (NodeWrapper)o;
      if (myNode != null ? !myNode.equals(that.myNode) : that.myNode != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return myNode.hashCode();
    }

    @Override
    public String toString() {
      return "node wrapper[" + myNode + "]";
    }
  }

  private static class TokenNodeWrapper extends NodeWrapper implements LighterASTTokenNode {
    TokenNodeWrapper(final ASTNode node) {
      super(node);
    }

    @Override
    public CharSequence getText() {
      return myNode.getText();
    }

    @Override
    public String toString() {
      return "token wrapper[" + myNode + "]";
    }
  }
}
