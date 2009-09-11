/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferLocalVariablesLiteralsAndAnnoMethodsWeigher extends CompletionWeigher {

  enum MyResult {
    classLiteral,
    normal,
    superMethodParameters,
    localOrParameter,
    annoMethod,
  }

  public MyResult weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    final Object object = item.getObject();

    if (location.getCompletionType() == CompletionType.SMART) {
      if (object instanceof PsiLocalVariable || object instanceof PsiParameter || object instanceof PsiThisExpression) {
        return MyResult.localOrParameter;
      }
      if (object instanceof String && item.getUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS) == Boolean.TRUE) {
        return MyResult.superMethodParameters;
      }
      return MyResult.normal;
    }

    if (location.getCompletionType() == CompletionType.BASIC) {
      if (object instanceof PsiKeyword && PsiKeyword.CLASS.equals(item.getLookupString())) {
        return MyResult.classLiteral;
      }

      if (object instanceof PsiAnnotationMethod && ((PsiAnnotationMethod)object).getContainingClass().isAnnotationType()) {
        return MyResult.annoMethod;
      }
    }

    return MyResult.normal;
  }
}
