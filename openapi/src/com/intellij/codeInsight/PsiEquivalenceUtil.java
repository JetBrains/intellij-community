package com.intellij.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class PsiEquivalenceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.PsiEquivalenceUtil");

  public static boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    if (element1.getNode().getElementType() != element2.getNode().getElementType()) return false;

    PsiElement[] children1 = getFilteredChildren(element1);
    PsiElement[] children2 = getFilteredChildren(element2);
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!areElementsEquivalent(child1, child2)) return false;
    }

    if (children1.length == 0) {
      if (!element1.textMatches(element2)) return false;
    }

    PsiReference ref1 = element1.getReference();
    if (ref1 != null) {
      PsiReference ref2 = element2.getReference();
      if (ref2 == null) return false;
      if (!Comparing.equal(ref1.resolve(), ref2.resolve())) return false;
    }
    return true;
  }

  private static PsiElement[] getFilteredChildren(PsiElement element1) {
    PsiElement[] children1 = element1.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    for (PsiElement child : children1) {
      if (!(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
    }
    return array.toArray(new PsiElement[array.size()]);
  }

  public static void findChildRangeDuplicates(PsiElement first, PsiElement last,
                                              List<Pair<PsiElement, PsiElement>> result,
                                              PsiElement scope) {
    LOG.assertTrue(first.getParent() == last.getParent());
    LOG.assertTrue(!(first instanceof PsiWhiteSpace) && !(last instanceof PsiWhiteSpace));
    addRangeDuplicates(scope, first, last, result);
  }

  private static void addRangeDuplicates(final PsiElement scope,
                                         final PsiElement first,
                                         final PsiElement last,
                                         final List<Pair<PsiElement, PsiElement>> result) {
    final PsiElement[] children = getFilteredChildren(scope);
    NextChild:
    for (int i = 0; i < children.length;) {
      PsiElement child = children[i];
      if (child != first) {
        int j = i;
        PsiElement next = first;
        do {
          if (!areElementsEquivalent(children[j], next)) break;
          j++;
          if (next == last) {
            result.add(new Pair<PsiElement, PsiElement>(child, children[j - 1]));
            i = j + 1;
            continue NextChild;
          }
          next = PsiTreeUtil.skipSiblingsForward(next, new Class[]{PsiWhiteSpace.class});
        }
        while (true);

        if (i == j) {
          addRangeDuplicates(child, first, last, result);
        }
      }

      i++;
    }
  }
}
