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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author peter
*/
public class PrefixMatchingWeigher extends CompletionWeigher {

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public static int getPrefixMatchingDegree(LookupElement item, CompletionLocation location) {
    final MinusculeMatcher matcher = getMinusculeMatcher(location.getCompletionParameters().getLookup().itemPattern(item));

    int max = Integer.MIN_VALUE;
    for (String lookupString : item.getAllLookupStrings()) {
      max = Math.max(max, matcher.matchingDegree(lookupString));
    }
    return max;
  }

  private static MinusculeMatcher getMinusculeMatcher(String prefix) {
    final int setting = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    final NameUtil.MatchingCaseSensitivity sensitivity =
      setting == CodeInsightSettings.NONE ? NameUtil.MatchingCaseSensitivity.NONE :
      setting == CodeInsightSettings.FIRST_LETTER ? NameUtil.MatchingCaseSensitivity.FIRST_LETTER : NameUtil.MatchingCaseSensitivity.ALL;
    return new MinusculeMatcher(CamelHumpMatcher.applyMiddleMatching(prefix), sensitivity);
  }

  public static StartMatchingDegree getStartMatchingDegree(LookupElement element, CompletionLocation location) {
    StartMatchingDegree result = StartMatchingDegree.middleMatch;
    String prefix = location.getCompletionParameters().getLookup().itemPattern(element);
    if (StringUtil.isNotEmpty(prefix)) {
      MinusculeMatcher matcher = getMinusculeMatcher(prefix);
      for (String ls : element.getAllLookupStrings()) {
        Iterable<TextRange> fragments = matcher.matchingFragments(ls);
        if (fragments != null) {
          Iterator<TextRange> iterator = fragments.iterator();
          if (!ls.isEmpty() && prefix.charAt(0) == ls.charAt(0)) {
            return StartMatchingDegree.startMatchSameCase;
          }
          if (iterator.hasNext() && iterator.next().contains(0)) {
            result = StartMatchingDegree.startMatchDifferentCase;
          }
        }
      }
    }
    return result;
  }

  public enum StartMatchingDegree {
    startMatchSameCase, startMatchDifferentCase, middleMatch
  }
}
