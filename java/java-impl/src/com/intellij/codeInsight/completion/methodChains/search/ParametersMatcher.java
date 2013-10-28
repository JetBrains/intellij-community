package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.codeInsight.completion.methodChains.completion.context.ChainCompletionContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * @author Dmitry Batkovich
 */
public class ParametersMatcher {

  public static MatchResult matchParameters(final MethodsChain chain, final ChainCompletionContext context) {
    MatchResult overallResult = EMPTY;
    for (final PsiMethod[] methods : chain.getPath()) {
      final NavigableSet<MatchResult> matchResults = new TreeSet<MatchResult>();
      for (final PsiMethod method : methods) {
        matchResults.add(matchParameters(method, context));
      }
      final MatchResult best = matchResults.first();
      overallResult = overallResult.add(best);
    }
    return overallResult;
  }

  public static MatchResult matchParameters(final PsiMethod method, final ChainCompletionContext context) {
    int matched = 0;
    int unMatched = 0;
    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiType type = parameter.getType();
      if (context.contains(type.getCanonicalText()) || type instanceof PsiPrimitiveType) {
        matched++;
      }
      else {
        unMatched++;
      }
    }
    return new MatchResult(matched, unMatched);
  }

  private static final MatchResult EMPTY = new MatchResult(0, 0);

  public static class MatchResult implements Comparable<MatchResult> {
    private final int myMatched;
    private final int myUnMatched;

    private MatchResult(final int matched, final int unMatched) {
      myMatched = matched;
      myUnMatched = unMatched;
    }

    public int getMatched() {
      return myMatched;
    }

    public int getUnMatched() {
      return myUnMatched;
    }

    public MatchResult add(final MatchResult other) {
      return new MatchResult(getMatched() + other.getMatched(), getUnMatched() + other.getUnMatched());
    }

    public boolean noUnmatchedAndHasMatched() {
      return myUnMatched == 0 && myMatched != 0;
    }

    @Override
    public int compareTo(@NotNull final MatchResult other) {
      final int sub = getUnMatched() - other.getUnMatched();
      if (sub != 0) {
        return sub;
      }
      return getMatched() - other.getMatched();
    }
  }
}
