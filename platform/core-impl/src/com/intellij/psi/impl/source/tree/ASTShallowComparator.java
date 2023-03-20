// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.ThreeState;
import com.intellij.util.diff.ShallowNodeComparator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ASTShallowComparator implements ShallowNodeComparator<ASTNode, ASTNode> {
  private final ProgressIndicator myIndicator;

  public ASTShallowComparator(@NotNull ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  @NotNull
  @Override
  public ThreeState deepEqual(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
    return textMatches(oldNode, newNode);
  }

  private ThreeState textMatches(ASTNode oldNode, ASTNode newNode) {
    myIndicator.checkCanceled();
    String oldText = TreeUtil.isCollapsedChameleon(oldNode) ? oldNode.getText() : null;
    String newText = TreeUtil.isCollapsedChameleon(newNode) ? newNode.getText() : null;
    if (oldText != null && newText != null) return oldText.equals(newText) ? ThreeState.YES : ThreeState.UNSURE;

    if (oldText != null) {
      return compareTreeToText((TreeElement)newNode, oldText) ? ThreeState.YES : ThreeState.UNSURE;
    }
    if (newText != null) {
      return compareTreeToText((TreeElement)oldNode, newText) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (oldNode instanceof ForeignLeafPsiElement) {
      return newNode instanceof ForeignLeafPsiElement && oldNode.getText().equals(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
    }

    if (newNode instanceof ForeignLeafPsiElement) return ThreeState.NO;

    if (oldNode instanceof LeafElement) {
      return ((LeafElement)oldNode).textMatches(newNode.getChars()) ? ThreeState.YES : ThreeState.NO;
    }
    if (newNode instanceof LeafElement) {
      return ((LeafElement)newNode).textMatches(oldNode.getChars()) ? ThreeState.YES : ThreeState.NO;
    }

    if (oldNode instanceof PsiErrorElement && newNode instanceof PsiErrorElement) {
      PsiErrorElement e1 = (PsiErrorElement)oldNode;
      PsiErrorElement e2 = (PsiErrorElement)newNode;
      if (!Objects.equals(e1.getErrorDescription(), e2.getErrorDescription())) return ThreeState.NO;
    }

    return ThreeState.UNSURE;
  }

  // have to perform tree walking by hand here to be able to interrupt ourselves
  private boolean compareTreeToText(@NotNull TreeElement root, @NotNull String text) {
    int[] curOffset = {0};
    root.acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitLeaf(LeafElement leaf) {
        matchText(leaf);
      }

      private void matchText(TreeElement leaf) {
        curOffset[0] = leaf.textMatches(text, curOffset[0]);
        if (curOffset[0] < 0) {
          stopWalking();
        }
      }

      @Override
      public void visitComposite(CompositeElement composite) {
        myIndicator.checkCanceled();
        if (composite instanceof LazyParseableElement && !((LazyParseableElement)composite).isParsed()) {
          matchText(composite);
        }
        else {
          super.visitComposite(composite);
        }
      }
    });
    return curOffset[0] == text.length();
  }

  @Override
  public boolean typesEqual(@NotNull ASTNode n1, @NotNull ASTNode n2) {
    return n1.getElementType() == n2.getElementType();
  }

  @Override
  public boolean hashCodesEqual(@NotNull ASTNode n1, @NotNull ASTNode n2) {
    if (n1 instanceof LeafElement && n2 instanceof LeafElement) {
      return textMatches(n1, n2) == ThreeState.YES;
    }

    if (n1 instanceof PsiErrorElement && n2 instanceof PsiErrorElement) {
      PsiErrorElement e1 = (PsiErrorElement)n1;
      PsiErrorElement e2 = (PsiErrorElement)n2;
      if (!Objects.equals(e1.getErrorDescription(), e2.getErrorDescription())) return false;
    }

    return ((TreeElement)n1).hc() == ((TreeElement)n2).hc();
  }
}
