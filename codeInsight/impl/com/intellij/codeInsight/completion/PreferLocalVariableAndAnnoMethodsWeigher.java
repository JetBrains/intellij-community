/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiThisExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferLocalVariableAndAnnoMethodsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    final Object object = item.getObject();

    if (location.getCompletionType() == CompletionType.SMART) {
      return object instanceof PsiLocalVariable || object instanceof PsiParameter || object instanceof PsiThisExpression;
    }
    else if (location.getCompletionType() == CompletionType.BASIC) {
      return object instanceof PsiAnnotationMethod && ((PsiAnnotationMethod) object).getContainingClass().isAnnotationType();
    }

    return false;
  }
}
