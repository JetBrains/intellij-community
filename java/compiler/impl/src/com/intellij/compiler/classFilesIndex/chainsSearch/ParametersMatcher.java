/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Dmitry Batkovich
 */
public final class ParametersMatcher {

  private ParametersMatcher() {}

  public static MatchResult matchParameters(final MethodsChain chain, final ChainCompletionContext context) {
    MatchResult overallResult = EMPTY;
    for (final PsiMethod[] methods : chain.getPath()) {
      final NavigableSet<MatchResult> matchResults = new TreeSet<>();
      for (final PsiMethod method : methods) {
        matchResults.add(matchParameters(method, context, chain.getExcludedQNames()));
      }
      final MatchResult best = matchResults.first();
      overallResult = overallResult.add(best);
    }
    return overallResult;
  }

  private static MatchResult matchParameters(final PsiMethod method, final ChainCompletionContext context, final Set<String> additionalExcludedNames) {
    int matched = 0;
    int unMatched = 0;
    boolean hasTarget = false;
    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiType type = parameter.getType();
      final String canonicalText = type.getCanonicalText();
      if (context.contains(canonicalText) || type instanceof PsiPrimitiveType) {
        matched++;
      }
      else {
        unMatched++;
      }
      if (context.getTarget().getClassQName().equals(canonicalText) || additionalExcludedNames.contains(canonicalText)) {
        hasTarget = true;
      }
    }
    return new MatchResult(matched, unMatched, hasTarget);
  }

  private static final MatchResult EMPTY = new MatchResult(0, 0, false);

  public static class MatchResult implements Comparable<MatchResult> {
    private final int myMatched;
    private final int myUnMatched;
    private final boolean myHasTarget;

    private MatchResult(final int matched, final int unMatched, final boolean hasTarget) {
      myMatched = matched;
      myUnMatched = unMatched;
      myHasTarget = hasTarget;
    }

    public int getMatched() {
      return myMatched;
    }

    public int getUnMatched() {
      return myUnMatched;
    }

    public boolean hasTarget() {
      return myHasTarget;
    }

    public MatchResult add(final MatchResult other) {
      return new MatchResult(getMatched() + other.getMatched(), getUnMatched() + other.getUnMatched(), other.myHasTarget || myHasTarget);
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
