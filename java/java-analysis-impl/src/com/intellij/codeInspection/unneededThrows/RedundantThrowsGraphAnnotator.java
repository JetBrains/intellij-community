/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefGraphAnnotatorEx;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.psi.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
        PsiElement body = null;
        PsiElement topElement = null;
        if (expression instanceof PsiLambdaExpression) {
          body = ((PsiLambdaExpression)expression).getBody();
          topElement = expression;
        }
        else {
          final PsiElement resolve = ((PsiMethodReferenceExpression)expression).resolve();
          if (resolve instanceof PsiMethod) {
            body = ((PsiMethod)resolve).getBody();
            topElement = resolve;
          }
        }

        final Collection<PsiClassType> exceptionTypes = body != null ? ExceptionUtil.collectUnhandledExceptions(body, topElement, false)
                                                                     : Collections.emptyList();


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
