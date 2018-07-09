// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;

import static com.intellij.codeInsight.javadoc.JavaDocUtil.resolveClassInTagValue;

/**
 * @author mike
 */
class ExceptionTagInfo extends ClassReferenceTagInfo {
  public ExceptionTagInfo(String name) {
    super(name);
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    return element instanceof PsiMethod;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    String result = super.checkTagValue(value);
    if (result != null) return result;

    PsiClass exceptionClass = resolveClassInTagValue(value);
    if (exceptionClass == null) return null;

    PsiClass throwable = JavaPsiFacade.getInstance(value.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, value.getResolveScope());
    if (throwable != null && !exceptionClass.equals(throwable) && !exceptionClass.isInheritor(throwable, true)) {
      return JavaErrorMessages.message("javadoc.exception.tag.class.is.not.throwable", exceptionClass.getQualifiedName());
    }

    PsiClass runtimeException = JavaPsiFacade.getInstance(value.getProject()).findClass(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, value.getResolveScope());
    if (runtimeException != null && (exceptionClass.isInheritor(runtimeException, true) || exceptionClass.equals(runtimeException))) {
      return null;
    }

    PsiClass errorException = JavaPsiFacade.getInstance(value.getProject()).findClass(CommonClassNames.JAVA_LANG_ERROR, value.getResolveScope());
    if (errorException != null && (exceptionClass.isInheritor(errorException, true) || exceptionClass.equals(errorException))) {
      return null;
    }

    PsiMethod method = PsiTreeUtil.getParentOfType(value, PsiMethod.class);
    if (method == null) return null;

    for (PsiClassType reference : method.getThrowsList().getReferencedTypes()) {
      PsiClass psiClass = reference.resolve();
      if (psiClass != null && (exceptionClass.isInheritor(psiClass, true) || exceptionClass.equals(psiClass))) {
        return null;
      }
    }

    return JavaErrorMessages.message("javadoc.exception.tag.exception.is.not.thrown", exceptionClass.getName(), method.getName());
  }
}