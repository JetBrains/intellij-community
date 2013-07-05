package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
public class SimpleDuplicatesFinder {
  private final ArrayList<PsiElement> myPattern;

  public SimpleDuplicatesFinder(@NotNull final PsiElement statement1, @NotNull final PsiElement statement2) {
    myPattern = new ArrayList<PsiElement>();
    PsiElement sibling = statement1;

    do {
      myPattern.add(sibling);
      if (sibling == statement2) break;
      sibling = PsiTreeUtil.skipSiblingsForward(sibling, PsiWhiteSpace.class, PsiComment.class);
    } while (sibling != null);
  }

  public List<Pair<PsiElement, PsiElement>> findDuplicates(@Nullable final List<PsiElement> scope, @NotNull final PsiElement generatedMethod) {
    final ArrayList<Pair<PsiElement, PsiElement>> result = new ArrayList<Pair<PsiElement, PsiElement>>();
    if (scope != null) {
      for (PsiElement element : scope) {
        findPatternOccurrences(result, element, generatedMethod);
      }
    }
    return result;
  }

  private void findPatternOccurrences(@NotNull final List<Pair<PsiElement, PsiElement>> array, @NotNull final PsiElement scope,
                                      @NotNull final PsiElement generatedMethod) {
    if (scope == generatedMethod) return;
    final PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      final Pair<PsiElement, PsiElement> match = isDuplicateFragment(child);
      if (match != null) {
        array.add(match);
        continue;
      }
      findPatternOccurrences(array, child, generatedMethod);
    }
  }

  @Nullable
  private Pair<PsiElement, PsiElement> isDuplicateFragment(@NotNull final PsiElement candidate) {
    for (PsiElement pattern : myPattern) {
      if (PsiTreeUtil.isAncestor(pattern, candidate, false)) return null;
    }
    PsiElement sibling = candidate;
    final ArrayList<PsiElement> candidates = new ArrayList<PsiElement>();
    for (int i = 0; i != myPattern.size(); ++i) {
      if (sibling == null) return null;

      candidates.add(sibling);
      sibling = PsiTreeUtil.skipSiblingsForward(sibling, PsiWhiteSpace.class, PsiComment.class);
    }
    if (myPattern.size() != candidates.size()) return null;
    if (candidates.size() <= 0) return null;
    final Pair<PsiElement, PsiElement> match = new Pair<PsiElement, PsiElement>(candidates.get(0), candidates.get(candidates.size() - 1));
    for (int i = 0; i < myPattern.size(); i++) {
      if (!matchPattern(myPattern.get(i), candidates.get(i))) return null;
    }
    return match;
  }

  private static boolean matchPattern(@Nullable final PsiElement pattern,
                                      @Nullable final PsiElement candidate) {
    if (pattern == null || candidate == null) return pattern == candidate;
    final PsiElement[] children1 = PsiEquivalenceUtil.getFilteredChildren(pattern, null, true);
    final PsiElement[] children2 = PsiEquivalenceUtil.getFilteredChildren(candidate, null, true);
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      PsiElement child1 = children1[i];
      PsiElement child2 = children2[i];
      if (!matchPattern(child1, child2)) return false;
    }

    if (children1.length == 0) {
      if (!pattern.textMatches(candidate)) return false;
    }

    return true;
  }

}
