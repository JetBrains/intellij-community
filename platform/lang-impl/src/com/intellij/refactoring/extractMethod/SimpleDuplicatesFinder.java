package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
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

  public List<Match> findDuplicates(@Nullable final List<PsiElement> scope,
                                    @NotNull final PsiElement generatedMethod,
                                    @NotNull final IElementType identifierType) {
    final List<Match> result = new ArrayList<Match>();
    if (scope != null) {
      for (PsiElement element : scope) {
        findPatternOccurrences(result, element, generatedMethod, identifierType);
      }
    }
    return result;
  }

  private void findPatternOccurrences(@NotNull final List<Match> array, @NotNull final PsiElement scope,
                                      @NotNull final PsiElement generatedMethod, @NotNull final IElementType identifierType) {
    if (scope == generatedMethod) return;
    final PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      final Match match = isDuplicateFragment(child, identifierType);
      if (match != null) {
        array.add(match);
        continue;
      }
      findPatternOccurrences(array, child, generatedMethod, identifierType);
    }
  }

  @Nullable
  protected Match isDuplicateFragment(@NotNull final PsiElement candidate, @NotNull final IElementType identifierType) {
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
    final Match match = new Match(candidates.get(0), candidates.get(candidates.size() - 1));
    for (int i = 0; i < myPattern.size(); i++) {
      if (!matchPattern(myPattern.get(i), candidates.get(i), identifierType, match)) return null;
    }
    return match;
  }

  private static boolean matchPattern(@Nullable final PsiElement pattern,
                                      @Nullable final PsiElement candidate,
                                      @NotNull final IElementType identifierType,
                                      @NotNull final Match match) {
    if (pattern == null || candidate == null) return pattern == candidate;
    final PsiElement[] children1 = PsiEquivalenceUtil.getFilteredChildren(pattern, null, true);
    final PsiElement[] children2 = PsiEquivalenceUtil.getFilteredChildren(candidate, null, true);
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      final PsiElement child1 = children1[i];
      final PsiElement child2 = children2[i];
      if (!matchPattern(child1, child2, identifierType, match)) return false;
    }

    if (children1.length == 0) {
      final IElementType patternElementType = pattern.getNode().getElementType();
      final IElementType candidateElementType = candidate.getNode().getElementType();
      if (patternElementType == identifierType && candidateElementType == identifierType) {
        match.changeParameter(pattern.getText(), candidate.getText());
        return true;
      }
      if (!pattern.textMatches(candidate)) return false;
    }

    return true;
  }

}
