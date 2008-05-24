/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class NameEndMatchingDegreeWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> element, final CompletionLocation location) {
    final String name = JavaCompletionUtil.getLookupObjectName(element.getObject());
    return JavaCompletionUtil.getNameEndMatchingDegree(element.getObject(), name, JavaCompletionUtil.EXPECTED_TYPES.getValue(location),
                                                       element.getPrefixMatcher().getPrefix());
  }

}
