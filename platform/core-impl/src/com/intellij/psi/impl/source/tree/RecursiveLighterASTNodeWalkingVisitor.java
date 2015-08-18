/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.util.WalkingState;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public abstract class RecursiveLighterASTNodeWalkingVisitor extends LighterASTNodeVisitor {
  protected RecursiveLighterASTNodeWalkingVisitor(@NotNull LighterAST ast) {
    myWalkingState = new WalkingState<LighterASTNode>(new LighterASTTreeGuide(ast)) {
      @Override
      public void elementFinished(@NotNull LighterASTNode element) {
        RecursiveLighterASTNodeWalkingVisitor.this.elementFinished(element);
      }

      @Override
      public void visit(@NotNull LighterASTNode element) {
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

  private static class LighterASTTreeGuide implements WalkingState.TreeGuide<LighterASTNode> {
    private final Map<LighterASTNode,LighterASTNode> nextSibling = new THashMap<LighterASTNode, LighterASTNode>();
    private final Map<LighterASTNode,LighterASTNode> prevSibling = new THashMap<LighterASTNode, LighterASTNode>();
    private final Map<LighterASTNode,LighterASTNode> parent = new THashMap<LighterASTNode, LighterASTNode>();
    private final LighterAST ast;

    private LighterASTTreeGuide(@NotNull LighterAST ast) {
      this.ast = ast;
    }

    @Override
    public LighterASTNode getNextSibling(@NotNull LighterASTNode element) {
      return nextSibling.get(element);
    }

    @Override
    public LighterASTNode getPrevSibling(@NotNull LighterASTNode element) {
      return prevSibling.get(element);
    }

    @Override
    public LighterASTNode getFirstChild(@NotNull LighterASTNode element) {
      List<LighterASTNode> children = ast.getChildren(element);
      if (children.isEmpty()) {
        return null;
      }
      for (int i = 1; i < children.size(); i++) {
        LighterASTNode child = children.get(i);
        LighterASTNode left = children.get(i - 1);
        nextSibling.put(left, child);
        prevSibling.put(child, left);
        parent.put(child, element);
      }
      parent.put(children.get(0), element);
      ast.disposeChildren(children);
      return children.get(0);
    }

    @Override
    public LighterASTNode getParent(@NotNull LighterASTNode element) {
      return parent.get(element);
    }
 }

  private final WalkingState<LighterASTNode> myWalkingState;

  protected void elementFinished(@NotNull LighterASTNode element) {
  }

  @Override
  public void visitNode(@NotNull LighterASTNode element) {
    myWalkingState.elementStarted(element);
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
