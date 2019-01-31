package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
* @author oleg
*/
public class TreePsiFragment extends PsiFragment {
  private final NodeSpecificHasher myHasher;

  public TreePsiFragment(final NodeSpecificHasher hasher, final PsiElement root, final int cost) {
    super(root, cost);
    myHasher = hasher;
  }

  public TreePsiFragment(final NodeSpecificHasher hasher, final List<? extends PsiElement> element, final int from, final int to) {
    super(element, from, to);
    myHasher = hasher;
  }

  @Override
  public boolean isEqual(PsiElement[] elements, int discardCost) {
    if (elements.length != myElementAnchors.length) {
      return false;
    }

    for (int i = 0; i < myElementAnchors.length; i++) {
      PsiElement one = myElementAnchors[i].retrieve();
      PsiElement two = elements[i];

      if (one == null || two == null || !myHasher.areTreesEqual(one, two, discardCost)) {
        return false;
      }
    }

    return true;
  }
}
