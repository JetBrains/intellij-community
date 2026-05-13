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
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning;
import com.intellij.util.WalkingState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RecursiveTreeElementWalkingVisitor extends TreeElementVisitor implements PsiRecursiveVisitor {
  // in this visitor, we cache a version to avoid excessive access to thread locals.
  // sometimes we are not operating on top of versioned syntax trees, so we need to indicate that this optimization is disabled.
  private static final int OPTIMIZED_VERSION_ACCESS_DISABLED = -2;

  private final boolean myDoTransform;
  private final WalkingState<ASTNode> myWalkingState;
  private final long version;

  protected RecursiveTreeElementWalkingVisitor() {
    this(null, true);
  }

  @ApiStatus.Experimental
  protected RecursiveTreeElementWalkingVisitor(@NotNull ASTNode node) {
    this(node, true);
  }

  /**
   * @param node a node for which the visitor will run. User to derive PSI version and perform optimizations
   */
  @ApiStatus.Experimental
  protected RecursiveTreeElementWalkingVisitor(@Nullable ASTNode node, boolean doTransform) {
    myDoTransform = doTransform;
    version = node instanceof TreeElement ? ((TreeElement) node).isVersioned() ? InternalPsiVersioning.getCurrentPsiVersion() : -1 : OPTIMIZED_VERSION_ACCESS_DISABLED;
    myWalkingState = new WalkingState<ASTNode>(new ASTTreeGuide(version)) {
      @Override
      public void elementFinished(@NotNull ASTNode element) {
        RecursiveTreeElementWalkingVisitor.this.elementFinished(element);
      }

      @Override
      public void visit(@NotNull ASTNode element) {
        ((TreeElement)element).acceptTree(RecursiveTreeElementWalkingVisitor.this);
      }
    };
  }

  private static class ASTTreeGuide implements WalkingState.TreeGuide<ASTNode> {
    private final long version;

    ASTTreeGuide(long version) {
      this.version = version;
    }

    private boolean isVersionAccessOptimizationEnabled(@NotNull ASTNode node) {
      return version != OPTIMIZED_VERSION_ACCESS_DISABLED && node instanceof TreeElement;
    }

    @Override
    public ASTNode getNextSibling(@NotNull ASTNode element) {
      return version != OPTIMIZED_VERSION_ACCESS_DISABLED && element instanceof TreeElement ? ((TreeElement)element).getTreeNextVersioned(version) : element.getTreeNext();
    }

    @Override
    public ASTNode getPrevSibling(@NotNull ASTNode element) {
      return isVersionAccessOptimizationEnabled(element) ? ((TreeElement)element).getTreePrevVersioned(version) : element.getTreePrev();
    }

    @Override
    public ASTNode getFirstChild(@NotNull ASTNode element) {
      return isVersionAccessOptimizationEnabled(element) ? ((TreeElement)element).getFirstChildNodeVersioned(version) : element.getFirstChildNode();
    }

    @Override
    public ASTNode getParent(@NotNull ASTNode element) {
      return isVersionAccessOptimizationEnabled(element)  ? ((TreeElement)element).getTreeParentVersioned(version) : element.getTreeParent();
    }

  }

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
    if (myDoTransform || !TreeUtil.isCollapsedChameleonVersioned(element, version)) {
      myWalkingState.elementStarted(element);
    }
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }
}
