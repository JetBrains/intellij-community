package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.AbstractVariableData;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
public class SimpleDuplicatesFinder {
  private static final Key<PsiElement> PARAMETER = Key.create("PARAMETER");
  
  protected PsiElement myReplacement;
  private final ArrayList<PsiElement> myPattern;
  private final Set<String> myParameters;
  private final Collection<String> myOutputVariables;

  @Deprecated
  public SimpleDuplicatesFinder(@NotNull final PsiElement statement1,
                                @NotNull final PsiElement statement2,
                                com.intellij.refactoring.extractMethod.AbstractVariableData[] variableData,
                                Collection<String> variables) {
    this(statement1, statement2, variables, variableData);
  }

  public SimpleDuplicatesFinder(@NotNull final PsiElement statement1,
                                @NotNull final PsiElement statement2,
                                Collection<String> variables,
                                AbstractVariableData[] variableData) {
    myOutputVariables = variables;
    myParameters = new HashSet<String>();
    for (AbstractVariableData data : variableData) {
      myParameters.add(data.getOriginalName());
    }
    myPattern = new ArrayList<PsiElement>();
    PsiElement sibling = statement1;

    do {
      myPattern.add(sibling);
      if (sibling == statement2) break;
      sibling = PsiTreeUtil.skipSiblingsForward(sibling, PsiWhiteSpace.class, PsiComment.class);
    } while (sibling != null);
  }

  public List<SimpleMatch> findDuplicates(@Nullable final List<PsiElement> scope,
                                          @NotNull final PsiElement generatedMethod) {
    final List<SimpleMatch> result = new ArrayList<SimpleMatch>();
    annotatePattern();
    if (scope != null) {
      for (PsiElement element : scope) {
        findPatternOccurrences(result, element, generatedMethod);
      }
    }
    deannotatePattern();
    return result;
  }

  private void deannotatePattern() {
    for (final PsiElement patternComponent : myPattern) {
      patternComponent.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override public void visitElement(PsiElement element) {
          if (element.getUserData(PARAMETER) != null) {
            element.putUserData(PARAMETER, null);
          }
        }
      });
    }
  }

  private void annotatePattern() {
    for (final PsiElement patternComponent : myPattern) {
      patternComponent.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          super.visitElement(element);
          if (myParameters.contains(element.getText())) {
            element.putUserData(PARAMETER, element);
          }

        }
      });
    }
  }

  private void findPatternOccurrences(@NotNull final List<SimpleMatch> array, @NotNull final PsiElement scope,
                                      @NotNull final PsiElement generatedMethod) {
    if (scope == generatedMethod) return;
    final PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      final SimpleMatch match = isDuplicateFragment(child);
      if (match != null) {
        array.add(match);
        continue;
      }
      findPatternOccurrences(array, child, generatedMethod);
    }
  }

  @Nullable
  protected SimpleMatch isDuplicateFragment(@NotNull final PsiElement candidate) {
    if (!canReplace(myReplacement, candidate)) return null;
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
    final SimpleMatch match = new SimpleMatch(candidates.get(0), candidates.get(candidates.size() - 1));
    for (int i = 0; i < myPattern.size(); i++) {
      if (!matchPattern(myPattern.get(i), candidates.get(i), match)) return null;
    }
    return match;
  }

  private boolean matchPattern(@Nullable final PsiElement pattern,
                                      @Nullable final PsiElement candidate,
                                      @NotNull final SimpleMatch match) {
    ProgressManager.checkCanceled();
    if (pattern == null || candidate == null) return pattern == candidate;
    final PsiElement[] children1 = PsiEquivalenceUtil.getFilteredChildren(pattern, null, true);
    final PsiElement[] children2 = PsiEquivalenceUtil.getFilteredChildren(candidate, null, true);
    final PsiElement patternParent = pattern.getParent();
    final PsiElement candidateParent = candidate.getParent();
    if (patternParent == null || candidateParent == null) return false;
    if (pattern.getUserData(PARAMETER) != null && patternParent.getClass() == candidateParent.getClass()) {
      match.changeParameter(pattern.getText(), candidate.getText());
      return true;
    }
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      final PsiElement child1 = children1[i];
      final PsiElement child2 = children2[i];
      if (!matchPattern(child1, child2, match)) return false;
    }

    if (children1.length == 0) {
      if (pattern.getUserData(PARAMETER) != null && patternParent.getClass() == candidateParent.getClass()) {
        match.changeParameter(pattern.getText(), candidate.getText());
        return true;
      }
      if (myOutputVariables.contains(pattern.getText())) {
        match.changeOutput(candidate.getText());
        return true;
      }
      if (!pattern.textMatches(candidate)) {
        return false;
      }
    }

    return true;
  }

  protected boolean canReplace(PsiElement replacement, PsiElement element) {
    return !PsiTreeUtil.isAncestor(replacement, element, false);
  }

  public void setReplacement(PsiElement replacement) {
    myReplacement = replacement;
  }

}
