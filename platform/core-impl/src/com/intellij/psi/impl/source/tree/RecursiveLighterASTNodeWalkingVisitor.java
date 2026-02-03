// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.WalkingState;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class RecursiveLighterASTNodeWalkingVisitor extends LighterASTNodeVisitor {
  private final @NotNull LighterAST ast;
  private final Stack<IndexedLighterASTNode> parentStack = new Stack<>();

  // wrapper around LighterASTNode which remembers its position in parents' children list for performance
  private static class IndexedLighterASTNode {
    private static final IndexedLighterASTNode[] EMPTY_ARRAY = new IndexedLighterASTNode[0];
    private final LighterASTNode node;
    private final IndexedLighterASTNode prev;
    private IndexedLighterASTNode next;

    IndexedLighterASTNode(@NotNull LighterASTNode node, IndexedLighterASTNode prev) {
      this.node = node;
      this.prev = prev;
    }
  }

  private class LighterASTGuide implements WalkingState.TreeGuide<IndexedLighterASTNode> {
    @Override
    public IndexedLighterASTNode getNextSibling(@NotNull IndexedLighterASTNode element) {
      return element.next;
    }

    @Override
    public IndexedLighterASTNode getPrevSibling(@NotNull IndexedLighterASTNode element) {
      return element.prev;
    }

    @Override
    public IndexedLighterASTNode getFirstChild(@NotNull IndexedLighterASTNode element) {
      List<LighterASTNode> children = ast.getChildren(element.node);
      IndexedLighterASTNode[] indexedChildren = children.isEmpty() ? IndexedLighterASTNode.EMPTY_ARRAY : new IndexedLighterASTNode[children.size()];
      for (int i = 0; i < children.size(); i++) {
        LighterASTNode child = children.get(i);
        IndexedLighterASTNode indexedNode = new IndexedLighterASTNode(child, i == 0 ? null : indexedChildren[i - 1]);
        indexedChildren[i] = indexedNode;
        if (i != 0) {
          indexedChildren[i-1].next = indexedNode;
        }
      }
      parentStack.push(element);
      return children.isEmpty() ? null : indexedChildren[0];
    }

    @Override
    public IndexedLighterASTNode getParent(@NotNull IndexedLighterASTNode element) {
      return parentStack.peek();
    }
  }

  protected RecursiveLighterASTNodeWalkingVisitor(@NotNull LighterAST ast) {
    this.ast = ast;

    myWalkingState = new WalkingState<IndexedLighterASTNode>(new LighterASTGuide()) {
      @Override
      public void elementFinished(@NotNull IndexedLighterASTNode element) {
        RecursiveLighterASTNodeWalkingVisitor.this.elementFinished(element.node);

        if (parentStack.peek() == element) { // getFirstChild returned nothing. otherwise getFirstChild() was not called, i.e. super.visitNode() was not called i.e. just ignore
          parentStack.pop();
        }
      }

      @Override
      public void visit(@NotNull IndexedLighterASTNode iNode) {
        ProgressManager.checkCanceled();
        LighterASTNode element = iNode.node;
        RecursiveLighterASTNodeWalkingVisitor visitor = RecursiveLighterASTNodeWalkingVisitor.this;
        if (element instanceof LighterLazyParseableNode) {
          visitor.visitLazyParseableNode((LighterLazyParseableNode)element);
        }
        else if (element instanceof LighterASTTokenNode) {
          visitor.visitTokenNode((LighterASTTokenNode)element);
        }
        else {
          visitor.visitNode(element);
        }
      }
    };
  }

  private final WalkingState<IndexedLighterASTNode> myWalkingState;

  protected void elementFinished(@NotNull LighterASTNode element) {
  }

  @Override
  public void visitNode(@NotNull LighterASTNode element) {
    myWalkingState.elementStarted(new IndexedLighterASTNode(element, null));
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
