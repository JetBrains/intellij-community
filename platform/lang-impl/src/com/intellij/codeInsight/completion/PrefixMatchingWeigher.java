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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PrefixMatchingWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @NotNull final CompletionLocation location) {
    final String prefix = location.getCompletionParameters().getLookup().itemPattern(item);

    final int setting = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    final NameUtil.MatchingCaseSensitivity sensitivity =
      setting == CodeInsightSettings.NONE ? NameUtil.MatchingCaseSensitivity.NONE :
      setting == CodeInsightSettings.FIRST_LETTER ? NameUtil.MatchingCaseSensitivity.FIRST_LETTER : NameUtil.MatchingCaseSensitivity.ALL;
    final NameUtil.MinusculeMatcher matcher = new NameUtil.MinusculeMatcher(prefix, sensitivity);

    int max = Integer.MIN_VALUE;
    for (String lookupString : item.getAllLookupStrings()) {
      max = Math.max(max, matcher.matchingDegree(lookupString));
    }
    return max;
  }
}
