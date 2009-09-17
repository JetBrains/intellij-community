/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferExpectedTypeWeigher extends CompletionWeigher {

  private enum MyResult {
    normal,
    expected,
    ofDefaultType,
  }

  public MyResult weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.BASIC) return MyResult.normal;
    if (item.getObject() instanceof PsiClass) return MyResult.normal;

    ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return MyResult.normal;

    PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
    if (itemType == null || !itemType.isValid()) return MyResult.normal;

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      final PsiType expectedType = expectedInfo.getType();
      if (!expectedType.isValid()) {
        return MyResult.normal;
      }

      if (defaultType != expectedType && defaultType.isAssignableFrom(itemType)) {
        return MyResult.ofDefaultType;
      }
      if (expectedType.isAssignableFrom(itemType)) {
        return MyResult.expected;
      }
    }

    return MyResult.normal;
  }
}
