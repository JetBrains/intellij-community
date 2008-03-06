/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferNonGenericWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    final Object object = item.getObject();
    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;

      PsiSubstitutor substitutor = (PsiSubstitutor)((LookupItem)item).getAttribute(LookupItem.SUBSTITUTOR);
      if (substitutor != null) {
        final PsiType type = substitutor.substitute(method.getReturnType());
        if (type instanceof PsiClassType && ((PsiClassType) type).resolve() instanceof PsiTypeParameter) return -1;
      }
    }

    return 0;
  }
}
