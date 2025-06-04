// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public final class JavaEditorTextProviderImpl implements EditorTextProvider {
  private static final Logger LOG = Logger.getInstance(JavaEditorTextProviderImpl.class);

  @Override
  public TextWithImports getEditorText(PsiElement elementAtCaret) {
    String result;
    PsiElement element = findExpression(elementAtCaret);
    if (element == null) return null;
    if (element instanceof PsiVariable) {
      result = qualifyEnumConstant(element, ((PsiVariable)element).getName());
    }
    else if (element instanceof PsiMethod) {
      result = ((PsiMethod)element).getName() + "()";
    }
    else if (element instanceof PsiReferenceExpression reference) {
      result = qualifyEnumConstant(reference.resolve(), element.getText());
    }
    else {
      result = element.getText();
    }
    return result != null ? new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result) : null;
  }

  private static @Nullable PsiElement findExpression(PsiElement element) {
    PsiElement e = PsiTreeUtil.getParentOfType(element, PsiVariable.class, PsiExpression.class, PsiMethod.class);
    if (e instanceof PsiVariable) {
      // return e;
    }
    else if (e instanceof PsiMethod && element.getParent() != e) {
      e = null;
    }
    else if (e instanceof PsiReferenceExpression) {
      if (e.getParent() instanceof PsiCallExpression) {
        e = e.getParent();
      }
      else if (e.getParent() instanceof PsiReferenceExpression) {
        // <caret>System.out case should not return plain class name
        PsiElement resolve = ((PsiReferenceExpression)e).resolve();
        if (resolve instanceof PsiClass) {
          e = e.getParent();
        }
      }
    }
    if (e instanceof PsiNewExpression) {
      // skip new Runnable() { ... }
      if (((PsiNewExpression)e).getAnonymousClass() != null) return null;
    }
    return e;
  }

  @Override
  public @Nullable Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    PsiElement expression = null;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiLiteralExpression) {
      if (((PsiLiteralExpression)parent).isTextBlock() && !allowMethodCalls) {
        return null;
      }
      element = parent;
      parent = parent.getParent();
    }
    else if (parent instanceof PsiLambdaExpression) {
      element = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiVariable) {
      expression = element;
    }
    else if (parent instanceof PsiReferenceExpression) {
      final PsiElement pparent = parent.getParent();
      if (parent instanceof PsiMethodReferenceExpression ||
          (pparent instanceof PsiCallExpression && ((PsiCallExpression)pparent).getArgumentList() != null)) { // skip arrays
        parent = pparent;
      }
      else if (pparent instanceof PsiReferenceExpression) {
        if (((PsiReferenceExpression)parent).resolve() instanceof PsiClass) {
          return findExpression(pparent, allowMethodCalls);
        }
      }
      if (allowMethodCalls || !DebuggerUtils.hasSideEffects(parent)) {
        expression = parent;
      }
    }
    else if (parent instanceof PsiThisExpression) {
      expression = parent;
    }
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
      if (allowMethodCalls) {
        expression = parent.getParent();
      }
    }
    else if (parent instanceof PsiArrayInitializerExpression) {
      if (allowMethodCalls) {
        PsiNewExpression newExpr = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
        if (newExpr != null) {
          expression = newExpr;
        }
      }
    }
    else if (parent instanceof PsiExpression &&
             !(parent instanceof PsiNewExpression) &&
             !(parent instanceof PsiLambdaExpression)) {
      if (allowMethodCalls || !DebuggerUtils.hasSideEffects(parent)) {
        expression = parent;
      }
    }
    else {
      PsiElement castExpr = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
      if (castExpr != null) {
        if (allowMethodCalls || !DebuggerUtils.hasSideEffects(castExpr)) {
          expression = castExpr;
        }
      }
      else if (allowMethodCalls) {
        PsiElement e = PsiTreeUtil.getParentOfType(element, PsiVariable.class, PsiExpression.class, PsiMethod.class);
        if (e instanceof PsiNewExpression) {
          if (((PsiNewExpression)e).getAnonymousClass() == null) {
            expression = e;
          }
        }
      }
    }

    if (expression != null) {
      try {
        PsiElement context = element;
        if (parent instanceof PsiParameter) {
          try {
            context = ((PsiMethod)((PsiParameter)parent).getDeclarationScope()).getBody();
          }
          catch (Throwable ignored) {
          }
        }
        else {
          while (context != null &&
                 !(context instanceof PsiStatement) &&
                 !(context instanceof PsiClass) &&
                 !(context instanceof PsiParameterListOwner)) {
            context = context.getParent();
          }
        }
        TextRange textRange = expression.getTextRange();
        PsiElement psiExpression = JavaPsiFacade.getElementFactory(expression.getProject())
          .createExpressionFromText(expression.getText(), context);
        return Pair.create(psiExpression, textRange);
      }
      catch (IncorrectOperationException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

  private static @Nullable String qualifyEnumConstant(PsiElement resolved, @Nullable String def) {
    if (resolved instanceof PsiEnumConstant enumConstant) {
      final PsiClass enumClass = enumConstant.getContainingClass();
      if (enumClass != null) {
        return enumClass.getName() + "." + enumConstant.getName();
      }
    }
    return def;
  }
}
