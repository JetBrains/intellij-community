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

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    final Object object = item.getObject();
    final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return 0;

    final PsiType itemType = JavaCompletionUtil.getPsiType(object);
    if (itemType == null) return 0;

    if (object instanceof PsiClass) {
      for (final ExpectedTypeInfo info : expectedInfos) {
        if (info.getType().getDeepComponentType().equals(itemType)) {
          return SkipAbstractExpectedTypeWeigher.getSkippingStatus(item, location) != SkipAbstractExpectedTypeWeigher.Result.ACCEPT ? 2 : 1;
        }
      }
    }

    return 0;
  }
}
