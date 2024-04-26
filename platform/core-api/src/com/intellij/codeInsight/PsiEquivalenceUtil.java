// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class PsiEquivalenceUtil {
  private static final Logger LOG = Logger.getInstance(PsiEquivalenceUtil.class);

  public static boolean areEquivalent(@NotNull PsiElement element1,
                                      @NotNull PsiElement element2,
                                      @Nullable BiPredicate<? super PsiElement, ? super PsiElement> resolvedElementsEqual,
                                      boolean areCommentsSignificant) {
    return areEquivalent(element1, element2, new ReferenceComparator(resolvedElementsEqual), null, null, areCommentsSignificant);
  }

  public static boolean areEquivalent(@NotNull PsiElement element1,
                                      @NotNull PsiElement element2,
                                      @Nullable BiPredicate<? super PsiElement, ? super PsiElement> resolvedElementsEqual,
                                      @Nullable BiPredicate<? super PsiElement, ? super PsiElement> leafsEqual) {
    return areEquivalent(element1, element2, new ReferenceComparator(resolvedElementsEqual), leafsEqual, null, false);
  }

  private static class ReferenceComparator implements BiPredicate<PsiReference, PsiReference> {
    private final @Nullable BiPredicate<? super PsiElement, ? super PsiElement> myResolvedElementsComparator;

    ReferenceComparator(@Nullable BiPredicate<? super PsiElement, ? super PsiElement> resolvedElementsComparator) {
      myResolvedElementsComparator = resolvedElementsComparator;
    }

    @Override
    public boolean test(PsiReference ref1, PsiReference ref2) {
      PsiElement resolved1 = ref1.resolve();
      PsiElement resolved2 = ref2.resolve();
      return Comparing.equal(resolved1, resolved2) ||
             myResolvedElementsComparator != null && myResolvedElementsComparator.test(resolved1, resolved2);
    }
  }

  public static boolean areEquivalent(@NotNull PsiElement element1,
                                      @NotNull PsiElement element2,
                                      @NotNull BiPredicate<? super PsiReference, ? super PsiReference> refsAreEqual,
                                      @Nullable BiPredicate<? super PsiElement, ? super PsiElement> leafsAreEqual,
                                      @Nullable Predicate<? super PsiElement> isElementSignificantCondition,
                                      boolean areCommentsSignificant) {
    if (element1 == element2) return true;
    ASTNode node1 = element1.getNode();
    ASTNode node2 = element2.getNode();
    if (node1 == null || node2 == null) return false;
    if (node1.getElementType() != node2.getElementType()) return false;

    PsiElement[] children1 = getFilteredChildren(element1, isElementSignificantCondition, areCommentsSignificant);
    PsiElement[] children2 = getFilteredChildren(element2, isElementSignificantCondition, areCommentsSignificant);
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!areEquivalent(child1, child2, refsAreEqual, leafsAreEqual, isElementSignificantCondition, areCommentsSignificant)) {
        return false;
      }
    }

    if (children1.length == 0) {
      if (leafsAreEqual != null) {
        if (!leafsAreEqual.test(element1, element2)) return false;
      }
      else {
        if (!element1.textMatches(element2)) return false;
      }
    }

    PsiReference ref1 = element1.getReference();
    if (ref1 != null) {
      PsiReference ref2 = element2.getReference();
      if (ref2 == null) return false;
      if (!refsAreEqual.test(ref1, ref2)) return false;
    }
    return true;
  }

  public static boolean areElementsEquivalent(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    return areEquivalent(element1, element2, null, false);
  }

  public static PsiElement @NotNull [] getFilteredChildren(final @NotNull PsiElement element,
                                                           @Nullable Predicate<? super PsiElement> isElementSignificantCondition,
                                                           boolean areCommentsSignificant) {
    ASTNode[] children1 = element.getNode().getChildren(null);
    ArrayList<PsiElement> array = new ArrayList<>();
    for (ASTNode node : children1) {
      final PsiElement child = node.getPsi();
      if (!(child instanceof PsiWhiteSpace) && (areCommentsSignificant || !(child instanceof PsiComment)) &&
          (isElementSignificantCondition == null || isElementSignificantCondition.test(child))) {
        array.add(child);
      }
    }
    return PsiUtilCore.toPsiElementArray(array);
  }

  public static void findChildRangeDuplicates(PsiElement first, PsiElement last, PsiElement scope,
                                              PairConsumer<? super PsiElement, ? super PsiElement> consumer) {
    LOG.assertTrue(first.getParent() == last.getParent());
    LOG.assertTrue(!(first instanceof PsiWhiteSpace) && !(last instanceof PsiWhiteSpace));
    addRangeDuplicates(scope, first, last, consumer);
  }

  private static void addRangeDuplicates(final PsiElement scope,
                                         final PsiElement first,
                                         final PsiElement last,
                                         final PairConsumer<? super PsiElement, ? super PsiElement> result) {
    final PsiElement[] children = getFilteredChildren(scope, null, true);
    NextChild:
    for (int i = 0; i < children.length;) {
      PsiElement child = children[i];
      if (child != first) {
        int j = i;
        PsiElement next = first;
        while (areElementsEquivalent(children[j], next)) {
          j++;
          if (next == last) {
            result.consume(child, children[j - 1]);
            i = j + 1;
            continue NextChild;
          }
          next = PsiTreeUtil.skipWhitespacesForward(next);
        }

        if (i == j) {
          addRangeDuplicates(child, first, last, result);
        }
      }

      i++;
    }
  }
}
