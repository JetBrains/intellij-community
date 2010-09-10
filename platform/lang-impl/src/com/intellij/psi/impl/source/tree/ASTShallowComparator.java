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
    if (TreeUtil.isCollapsedChameleon(oldNode)) {
      return ((TreeElement)newNode).textMatches(oldNode.getText()) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (TreeUtil.isCollapsedChameleon(newNode)) {
      return ((TreeElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.UNSURE;
    }

    if (oldNode instanceof LeafElement) {
      return ((LeafElement)oldNode).textMatches(newNode.getText()) ? ThreeState.YES : ThreeState.NO;
    }

    if (oldNode instanceof PsiErrorElement && newNode instanceof PsiErrorElement) {
      final PsiErrorElement e1 = (PsiErrorElement)oldNode;
      final PsiErrorElement e2 = (PsiErrorElement)newNode;
      if (!Comparing.equal(e1.getErrorDescription(), e2.getErrorDescription())) return ThreeState.NO;
    }

    return ThreeState.UNSURE;
  }

  public boolean typesEqual(final ASTNode n1, final ASTNode n2) {
    return n1.getElementType() == n2.getElementType();
  }

  public boolean hashCodesEqual(final ASTNode n1, final ASTNode n2) {
    if (n1 instanceof LeafElement && n2 instanceof LeafElement) {
      return textMatches(n1, n2) == ThreeState.YES;
    }

    if (n1 instanceof PsiErrorElement && n2 instanceof PsiErrorElement) {
      final PsiErrorElement e1 = (PsiErrorElement)n1;
      final PsiErrorElement e2 = (PsiErrorElement)n2;
      if (!Comparing.equal(e1.getErrorDescription(), e2.getErrorDescription())) return false;
    }

    return ((TreeElement)n1).hc() == ((TreeElement)n2).hc();
  }

}
