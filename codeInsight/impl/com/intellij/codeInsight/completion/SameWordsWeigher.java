/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.codeStyle.NameUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
*/
public class SameWordsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
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
