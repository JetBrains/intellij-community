/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class JavaEditorTextProviderImpl implements EditorTextProvider {
  private static final Logger LOG = Logger.getInstance(JavaEditorTextProviderImpl.class);

  @Override
  public TextWithImports getEditorText(PsiElement elementAtCaret) {
    String result = null;
    PsiElement element = findExpression(elementAtCaret);
    if (element != null) {
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression reference = (PsiReferenceExpression)element;
        if (reference.getQualifier() == null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiEnumConstant) {
            final PsiEnumConstant enumConstant = (PsiEnumConstant)resolved;
            final PsiClass enumClass = enumConstant.getContainingClass();
            if (enumClass != null) {
              result = enumClass.getName() + "." + enumConstant.getName();
            }
          }
        }
      }
      if (result == null) {
        result = element.getText();
      }
    }
    return result != null? new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result) : null;
  }

  @Nullable
  private static PsiElement findExpression(PsiElement element) {
    if (!(element instanceof PsiIdentifier || element instanceof PsiKeyword)) {
      return null;
    }
    PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable) {
      return element;
    }
    if (parent instanceof PsiReferenceExpression) {
      if (parent.getParent() instanceof PsiCallExpression) return parent.getParent();
      return parent;
    }
    if (parent instanceof PsiThisExpression) {
      return parent;
    }
    return null;
  }

  @Nullable
  public Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    if (!(element instanceof PsiIdentifier || element instanceof PsiKeyword)) {
      return null;
    }

    PsiElement expression = null;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable) {
      expression = element;
    }
    else if (parent instanceof PsiReferenceExpression) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiCallExpression) {
        parent = pparent;
      }
      if (allowMethodCalls || !DebuggerUtils.hasSideEffects(parent)) {
        expression = parent;
      }
    }
    else if (parent instanceof PsiThisExpression) {
      expression = parent;
    }

    if (expression != null) {
      try {
        PsiElement context = element;
        if(parent instanceof PsiParameter) {
          try {
            context = ((PsiMethod)((PsiParameter)parent).getDeclarationScope()).getBody();
          }
          catch (Throwable ignored) {
          }
        }
        else {
          while(context != null  && !(context instanceof PsiStatement) && !(context instanceof PsiClass)) {
            context = context.getParent();
          }
        }
        TextRange textRange = expression.getTextRange();
        PsiElement psiExpression = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory().createExpressionFromText(expression.getText(), context);
        return Pair.create(psiExpression, textRange);
      }
      catch (IncorrectOperationException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

}
