/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefGraphAnnotatorEx;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.psi.*;

import java.util.*;

public final class RedundantThrowsGraphAnnotator extends RefGraphAnnotatorEx {
  private final RefManager myRefManager;

  public RedundantThrowsGraphAnnotator(RefManager refManager) {
    myRefManager = refManager;
  }

  @Override
  public void onInitialize(RefElement refElement) {
    if (refElement instanceof RefMethodImpl) {
      PsiElement element = refElement.getElement();
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        final PsiCodeBlock body = method.getBody();
        if (body == null) return;

        final Collection<PsiClassType> exceptionTypes = getUnhandledExceptions(body, method, method.getContainingClass());
        for (final PsiClassType exceptionType : exceptionTypes) {
          ((RefMethodImpl)refElement).updateThrowsList(exceptionType);
        }
      }
    }
  }

  @Override
  public void onMarkReferenced(PsiElement what, PsiElement from, boolean referencedFromClassInitializer) {
    if (from instanceof PsiFunctionalExpression) {
      RefElement refResolved = myRefManager.getReference(what);
      if (refResolved instanceof RefMethodImpl) {
        PsiFunctionalExpression expression = (PsiFunctionalExpression)from;
        final Collection<PsiClassType> exceptionTypes;
        if (expression instanceof PsiLambdaExpression) {
          PsiElement body = ((PsiLambdaExpression)expression).getBody();
          exceptionTypes = body != null ? ExceptionUtil.collectUnhandledExceptions(body, expression, false) : Collections.emptyList();
        }
        else {
          final PsiElement resolve = ((PsiMethodReferenceExpression)expression).resolve();
          exceptionTypes = resolve instanceof PsiMethod
                           ? Arrays.asList(((PsiMethod)resolve).getThrowsList().getReferencedTypes())
                           : Collections.emptyList();
        }

        for (final PsiClassType exceptionType : exceptionTypes) {
          ((RefMethodImpl)refResolved).updateThrowsList(exceptionType);
        }
      }
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
