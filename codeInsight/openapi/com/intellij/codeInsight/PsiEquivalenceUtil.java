package com.intellij.codeInsight;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * @author ven
 */
public class PsiEquivalenceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.PsiEquivalenceUtil");

  public static boolean areElementsEquivalent(@NotNull PsiElement element1,
                                              @NotNull PsiElement element2,
                                              @Nullable Comparator<PsiElement> resolvedElementsComparator,
                                              boolean areCommentsSignificant) {
    if(element1 == element2) return true;
    ASTNode node1 = element1.getNode();
    ASTNode node2 = element2.getNode();
    if (node1 == null || node2 == null) return false;
    if (node1.getElementType() != node2.getElementType()) return false;

    PsiElement[] children1 = getFilteredChildren(element1, areCommentsSignificant);
    PsiElement[] children2 = getFilteredChildren(element2, areCommentsSignificant);
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!areElementsEquivalent(child1, child2, resolvedElementsComparator, areCommentsSignificant)) return false;
    }

    if (children1.length == 0) {
      if (!element1.textMatches(element2)) return false;
    }

    PsiReference ref1 = element1.getReference();
    if (ref1 != null) {
      PsiReference ref2 = element2.getReference();
      if (ref2 == null) return false;
      PsiElement resolved1 = ref1.resolve();
      PsiElement resolved2 = ref2.resolve();
      if (!Comparing.equal(resolved1, resolved2)
          && (resolvedElementsComparator == null || resolvedElementsComparator.compare(resolved1, resolved2) != 0)) return false;
    }
    return true;

  }
  public static boolean areElementsEquivalent(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    return areElementsEquivalent(element1, element2, null, false);
  }

  private static PsiElement[] getFilteredChildren(PsiElement element1, boolean areCommentsSignificant) {
    ASTNode[] children1 = element1.getNode().getChildren(null);
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    for (ASTNode node : children1) {
      final PsiElement child = node.getPsi();
      if (!(child instanceof PsiWhiteSpace) && (areCommentsSignificant || !(child instanceof PsiComment))) {
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
    final PsiElement[] children = getFilteredChildren(scope, true);
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
          next = PsiTreeUtil.skipSiblingsForward(next, PsiWhiteSpace.class);
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
