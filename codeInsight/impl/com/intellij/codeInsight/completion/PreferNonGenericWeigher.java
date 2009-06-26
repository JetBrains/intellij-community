/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferNonGenericWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    final Object object = item.getObject();
    if (object instanceof PsiMethod) {
      final PsiType type = JavaCompletionUtil.getLookupElementType(item);
      if (type instanceof PsiClassType && ((PsiClassType) type).resolve() instanceof PsiTypeParameter) return -1;
    }

    return 0;
  }
}
