/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferAccessibleWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    final Object object = item.getObject();
    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;
      if (!JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(method, location.getCompletionParameters().getPosition(), null)) return -2;
    }

    return 0;
  }
}
