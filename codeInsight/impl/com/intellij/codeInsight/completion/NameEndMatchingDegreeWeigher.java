/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class NameEndMatchingDegreeWeigher extends CompletionWeigher {

  public Integer weigh(@NotNull final LookupElement element, final CompletionLocation location) {
    if (!(element instanceof MutableLookupElement)) return 0;

    final Object o = ((MutableLookupElement)element).getObject();
    final String name = JavaCompletionUtil.getLookupObjectName(o);
    return JavaCompletionUtil.getNameEndMatchingDegree(o, name, JavaCompletionUtil.EXPECTED_TYPES.getValue(location),
                                                       element.getPrefixMatcher().getPrefix());
  }

}
