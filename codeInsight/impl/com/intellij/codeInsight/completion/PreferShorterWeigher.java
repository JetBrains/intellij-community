/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferShorterWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    final Object object = item.getObject();
    final String name = JavaCompletionUtil.getLookupObjectName(object);
    final ExpectedTypeInfo[] myExpectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);


    if (name != null && myExpectedInfos != null && JavaCompletionUtil.getNameEndMatchingDegree(object, name, myExpectedInfos, location.getPrefix()) != 0) {
      return 239 - NameUtil.nameToWords(name).length;
    }
    return 0;

  }
}
