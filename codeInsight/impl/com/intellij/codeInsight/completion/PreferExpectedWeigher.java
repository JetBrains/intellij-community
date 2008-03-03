/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferExpectedWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    final Object object = item.getObject();
    final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (expectedInfos == null) return 0;

    final PsiType itemType = JavaCompletionUtil.getPsiType(object);
    if (itemType == null) return 0;

    if (object instanceof PsiClass) {
      for (final ExpectedTypeInfo info : expectedInfos) {
        if(info.getType().getDeepComponentType().equals(itemType)) {
          return Integer.MAX_VALUE;
        }
        if(info.getDefaultType().getDeepComponentType().equals(itemType)) {
          return Integer.MAX_VALUE - 1;
        }
      }
    }

    int defaultStatus = 0;

    for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
      final PsiType defaultType = expectedInfo.getDefaultType();
      if (defaultType != expectedInfo.getType() && defaultType.isAssignableFrom(itemType)) {
        defaultStatus = 1;
        break;
      }
    }

    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;

      PsiSubstitutor substitutor = (PsiSubstitutor)((LookupItem)item).getAttribute(LookupItem.SUBSTITUTOR);
      if (substitutor != null) {
        final PsiType type = substitutor.substitute(method.getReturnType());
        if (type instanceof PsiClassType && ((PsiClassType) type).resolve() instanceof PsiTypeParameter) return -1;
      }

      if (!JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, location.getCompletionParameters().getPosition(), null)) return -2;
    }

    return defaultStatus;
  }
}
