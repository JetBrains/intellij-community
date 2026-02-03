// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.UnhandledExceptions;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefFunctionalExpression;
import com.intellij.codeInspection.reference.RefGraphAnnotatorEx;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.util.ObjectUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public final class RedundantThrowsGraphAnnotator extends RefGraphAnnotatorEx {
  private final RefManager myRefManager;

  public RedundantThrowsGraphAnnotator(RefManager refManager) {
    myRefManager = refManager;
  }

  @Override
  public void onInitialize(RefElement refElement) {
    if (refElement instanceof RefMethodImpl methodImpl) {
      if (refElement.getPsiElement() instanceof PsiMethod method) {
        final PsiCodeBlock body = method.getBody();
        if (body == null) return;

        UnhandledExceptions exceptions = UnhandledExceptions.ofMethod(method);
        if (exceptions.hasUnresolvedCalls()) {
          PsiClassType throwableType = JavaPsiFacade.getElementFactory(method.getProject())
            .createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, method.getResolveScope());
          methodImpl.updateThrowsList(throwableType);
        }
        final Collection<PsiClassType> exceptionTypes = exceptions.exceptions();
        for (final PsiClassType exceptionType : exceptionTypes) {
          methodImpl.updateThrowsList(exceptionType);
        }
      }
    }
    else if (refElement instanceof RefFunctionalExpression) {
      PsiElement expression = refElement.getPsiElement();
      Collection<PsiClassType> exceptionTypes = null;
      PsiMethod method = null;
      if (expression instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)expression).getBody();
        exceptionTypes = body != null ? ExceptionUtil.collectUnhandledExceptions(body, expression, false) : Collections.emptyList();
        method = LambdaUtil.getFunctionalInterfaceMethod(expression);
      }
      else if (expression instanceof PsiMethodReferenceExpression) {
        PsiMethod resolved = ObjectUtils.tryCast(((PsiMethodReferenceExpression)expression).resolve(), PsiMethod.class);
        if (resolved != null) {
          PsiClassType[] types = resolved.getThrowsList().getReferencedTypes();
          exceptionTypes = types.length == 0 ? Collections.emptyList() : Arrays.asList(types);
        }
        method = LambdaUtil.getFunctionalInterfaceMethod(expression);
      }
      if (method == null || exceptionTypes == null) return;
      RefElement refMethod = myRefManager.getReference(method);
      if (refMethod == null) return;
      Collection<PsiClassType> finalExceptionTypes = exceptionTypes;
      myRefManager.executeTask(() -> {
        refMethod.initializeIfNeeded();
        for (PsiClassType exceptionType : finalExceptionTypes) {
          ((RefMethodImpl)refMethod).updateThrowsList(exceptionType);
        }
      });
    }
  }
}
