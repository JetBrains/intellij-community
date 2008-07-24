/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferExpectedTypeWeigher extends CompletionWeigher {

  private enum MyResult {
    normal,
    ofDefaultType,
    expected,
    expectedNoSelect
  }

  public MyResult weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    if (!(item instanceof MutableLookupElement)) return MyResult.normal;

    final Object object = ((MutableLookupElement)item).getObject();
    final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return MyResult.normal;

    final PsiType itemType = JavaCompletionUtil.getPsiType(object);
    if (itemType == null) return MyResult.normal;

    if (object instanceof PsiClass) {
      for (final ExpectedTypeInfo info : expectedInfos) {
        if (TypeConversionUtil.erasure(info.getType().getDeepComponentType()).equals(TypeConversionUtil.erasure(itemType))) {
          return SkipAbstractExpectedTypeWeigher.getSkippingStatus(item, location) != SkipAbstractExpectedTypeWeigher.Result.ACCEPT ? MyResult.expectedNoSelect : MyResult.expected;
        }
      }
    }

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      if (defaultType != expectedInfo.getType() && defaultType.isAssignableFrom(itemType)) {
        return MyResult.ofDefaultType;
      }
    }

    return MyResult.normal;
  }
}
