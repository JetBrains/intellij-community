package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.ThreeState;
import com.intellij.util.diff.ShallowNodeComparator;

/**
 * @author max
 */
public class ASTShallowComparator implements ShallowNodeComparator<ASTNode, ASTNode> {
  public ThreeState deepEqual(final ASTNode oldNode, final ASTNode newNode) {
    return textMatches(oldNode, newNode);
  }

  private static ThreeState textMatches(final ASTNode oldNode, final ASTNode newNode) {
    if (oldNode instanceof ChameleonElement) {
      return ((TreeElement)newNode).textMatches(oldNode.getText()) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (newNode instanceof ChameleonElement) {
      return ((TreeElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (oldNode instanceof LeafElement) {
      if (newNode instanceof LeafElement) return ((LeafElement)oldNode).textMatches(((LeafElement)newNode).getInternedText()) ? ThreeState.YES : ThreeState.NO;
      return ((LeafElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
    }

    if (oldNode instanceof PsiErrorElement && newNode instanceof PsiErrorElement) {
      final PsiErrorElement e1 = ((PsiErrorElement)oldNode);
      final PsiErrorElement e2 = ((PsiErrorElement)newNode);
      if (!Comparing.equal(e1.getErrorDescription(), e2.getErrorDescription())) return ThreeState.NO;
    }

    return ThreeState.UNSURE;
  }

  public boolean typesEqual(final ASTNode n1, final ASTNode n2) {
    return n1.getElementType() == n2.getElementType();
  }

  public boolean hashcodesEqual(final ASTNode n1, final ASTNode n2) {
    if (n1 instanceof LeafElement && n2 instanceof LeafElement) {
      return textMatches(n1, n2) == ThreeState.YES;
    }

    if (n1 instanceof PsiErrorElement && n2 instanceof PsiErrorElement) {
      final PsiErrorElement e1 = ((PsiErrorElement)n1);
      final PsiErrorElement e2 = ((PsiErrorElement)n2);
      if (!Comparing.equal(e1.getErrorDescription(), e2.getErrorDescription())) return false;
    }

    return ((TreeElement)n1).hc() == ((TreeElement)n2).hc();
  }
}
