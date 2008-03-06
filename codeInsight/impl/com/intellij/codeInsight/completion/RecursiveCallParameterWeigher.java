/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class RecursiveCallParameterWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> element, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.SMART) return 0;

    final Object object = element.getObject();
    final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos != null) {
      final PsiType itemType = JavaCompletionUtil.getPsiType(object);
      if (itemType != null) {
        final PsiMethod positionMethod = JavaCompletionUtil.POSITION_METHOD.getValue(location);
        if (positionMethod != null) {
          for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
            if (expectedInfo.getCalledMethod() == positionMethod && expectedInfo.getType().isAssignableFrom(itemType)) {
              return 0;
            }
          }
        }
      }
    }
    return 1;
  }
}
