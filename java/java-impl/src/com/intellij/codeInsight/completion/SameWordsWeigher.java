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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.codeStyle.NameUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
*/
public class SameWordsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    final Object object = item.getObject();

    final String name = JavaCompletionUtil.getLookupObjectName(object);
    final ExpectedTypeInfo[] myExpectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (name != null && myExpectedInfos != null) {
      int max = 0;
      final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(JavaCompletionUtil.truncDigits(name));
      for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
        String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
        if (expectedName != null) {
          final THashSet<String> set = new THashSet<String>(NameUtil.nameToWordsLowerCase(JavaCompletionUtil.truncDigits(expectedName)));
          set.retainAll(wordsNoDigits);
          max = Math.max(max, set.size());
        }
      }
      return max;
    }
    return 0;
  }
}
