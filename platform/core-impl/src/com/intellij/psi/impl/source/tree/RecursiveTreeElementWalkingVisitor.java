/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiRecursiveVisitor;
import com.intellij.util.WalkingState;
import org.jetbrains.annotations.NotNull;

public abstract class RecursiveTreeElementWalkingVisitor extends TreeElementVisitor implements PsiRecursiveVisitor {
  private final boolean myDoTransform;

  protected RecursiveTreeElementWalkingVisitor() {
    this(true);
  }

  protected RecursiveTreeElementWalkingVisitor(boolean doTransform) {
    myDoTransform = doTransform;
  }

  private static class ASTTreeGuide implements WalkingState.TreeGuide<ASTNode> {
    @Override
    public ASTNode getNextSibling(@NotNull ASTNode element) {
      return element.getTreeNext();
    }

    @Override
    public ASTNode getPrevSibling(@NotNull ASTNode element) {
      return element.getTreePrev();
    }

    @Override
    public ASTNode getFirstChild(@NotNull ASTNode element) {
      return element.getFirstChildNode();
    }

    @Override
    public ASTNode getParent(@NotNull ASTNode element) {
      return element.getTreeParent();
    }

    private static final ASTTreeGuide instance = new ASTTreeGuide();
  }

  private final WalkingState<ASTNode> myWalkingState = new WalkingState<ASTNode>(ASTTreeGuide.instance) {
    @Override
    public void elementFinished(@NotNull ASTNode element) {
      RecursiveTreeElementWalkingVisitor.this.elementFinished(element);
    }

    @Override
    public void visit(@NotNull ASTNode element) {
      ((TreeElement)element).acceptTree(RecursiveTreeElementWalkingVisitor.this);
    }
  };

  protected void elementFinished(@NotNull ASTNode element) {
  }

  @Override
  public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  @Override
  public void visitComposite(CompositeElement composite) {
    visitNode(composite);
  }

  protected void visitNode(TreeElement element) {
    if (myDoTransform || !TreeUtil.isCollapsedChameleon(element)) {
      myWalkingState.elementStarted(element);
    }
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
