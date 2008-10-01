/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferShorterWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    if (!(item instanceof MutableLookupElement)) return 0;

    final Object object = ((MutableLookupElement)item).getObject();
    final String name = JavaCompletionUtil.getLookupObjectName(object);
    final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);

    if (name != null && expectedInfos != null && JavaCompletionUtil.getNameEndMatchingDegree(name, expectedInfos,
                                                                                             item.getPrefixMatcher().getPrefix()) != 0) {
      return 239 - NameUtil.nameToWords(name).length;
    }
    return 0;

  }
}
