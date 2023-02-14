// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;

import java.util.*;

public final class RedundantThrowsGraphAnnotator extends RefGraphAnnotatorEx {
  private final RefManager myRefManager;

  public RedundantThrowsGraphAnnotator(RefManager refManager) {
    myRefManager = refManager;
  }

  @Override
  public void onInitialize(RefElement refElement) {
    if (refElement instanceof RefMethodImpl) {
      if (refElement.getPsiElement() instanceof PsiMethod method) {
        final PsiCodeBlock body = method.getBody();
        if (body == null) return;

        final Collection<PsiClassType> exceptionTypes = getUnhandledExceptions(body, method, method.getContainingClass());
        for (final PsiClassType exceptionType : exceptionTypes) {
          ((RefMethodImpl)refElement).updateThrowsList(exceptionType);
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
          exceptionTypes = Arrays.asList(resolved.getThrowsList().getReferencedTypes());
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

  public static Set<PsiClassType> getUnhandledExceptions(PsiCodeBlock body, PsiMethod method, PsiClass containingClass) {
    Collection<PsiClassType> types = ExceptionUtil.collectUnhandledExceptions(body, method, false);
    Set<PsiClassType> unhandled = new HashSet<>(types);
    if (method.isConstructor()) {
      // there may be field initializer throwing exception
      // that exception must be caught in the constructor
      PsiField[] fields = containingClass.getFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) continue;
        unhandled.addAll(ExceptionUtil.collectUnhandledExceptions(initializer, field));
      }
    }
    return unhandled;
  }
}
