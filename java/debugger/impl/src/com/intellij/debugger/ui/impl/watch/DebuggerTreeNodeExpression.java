// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class DebuggerTreeNodeExpression {
  @Nullable
  public static PsiExpression substituteThis(@Nullable PsiElement expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue)
    throws EvaluateException {
    if (!(expressionWithThis instanceof PsiExpression)) return null;
    PsiExpression result = (PsiExpression)expressionWithThis.copy();

    PsiClass thisClass = PsiTreeUtil.getContextOfType(result, PsiClass.class, true);

    boolean castNeeded = true;

    if (thisClass != null) {
      PsiType type = howToEvaluateThis.getType();
      if (type != null) {
        if (type instanceof PsiClassType) {
          PsiClass psiClass = ((PsiClassType)type).resolve();
          if (psiClass != null && (psiClass == thisClass || psiClass.isInheritor(thisClass, true))) {
            castNeeded = false;
          }
        }
        else if (type instanceof PsiArrayType && PsiUtil.isArrayClass(thisClass)) {
          castNeeded = false;
        }
      }
    }

    if (castNeeded) {
      howToEvaluateThis = castToRuntimeType(howToEvaluateThis, howToEvaluateThisValue);
    }

    ChangeContextUtil.encodeContextInfo(result, false);
    PsiExpression psiExpression;
    try {
      psiExpression = (PsiExpression)ChangeContextUtil.decodeContextInfo(result, thisClass, howToEvaluateThis);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(
        JavaDebuggerBundle.message("evaluation.error.invalid.this.expression", result.getText(), howToEvaluateThis.getText()), null);
    }

    try {
      PsiExpression res = JavaPsiFacade.getElementFactory(howToEvaluateThis.getProject())
        .createExpressionFromText(psiExpression.getText(), howToEvaluateThis.getContext());
      res.putUserData(ADDITIONAL_IMPORTS_KEY, howToEvaluateThis.getUserData(ADDITIONAL_IMPORTS_KEY));
      return res;
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(e.getMessage(), e);
    }
  }

  public static final Key<Set<String>> ADDITIONAL_IMPORTS_KEY = Key.create("ADDITIONAL_IMPORTS");

  public static PsiExpression castToRuntimeType(PsiExpression expression, Value value) throws EvaluateException {
    if (!(value instanceof ObjectReference)) {
      return expression;
    }

    ReferenceType valueType = ((ObjectReference)value).referenceType();
    if (valueType == null) {
      return expression;
    }

    Project project = expression.getProject();

    PsiType type = RuntimeTypeEvaluator.getCastableRuntimeType(project, value);
    if (type == null) {
      return expression;
    }

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String typeName = type.getCanonicalText();
    try {
      PsiParenthesizedExpression parenthExpression = (PsiParenthesizedExpression)elementFactory.createExpressionFromText(
        "((" + typeName + ")expression)", null);
      //noinspection ConstantConditions
      ((PsiTypeCastExpression)parenthExpression.getExpression()).getOperand().replace(expression);
      Set<String> imports = expression.getUserData(ADDITIONAL_IMPORTS_KEY);
      if (imports == null) {
        imports = new HashSet<>();
      }
      imports.add(typeName);
      parenthExpression.putUserData(ADDITIONAL_IMPORTS_KEY, imports);
      return parenthExpression;
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(JavaDebuggerBundle.message("error.invalid.type.name", typeName), e);
    }
  }

  /**
   * @param qualifiedName the class qualified name to be resolved against the current execution context
   * @return short name if the class could be resolved using short name,
   * otherwise returns qualifiedName
   */
  public static String normalize(final String qualifiedName, PsiElement contextElement, Project project) {
    if (contextElement == null) {
      return qualifiedName;
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass aClass = facade.findClass(qualifiedName, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return normalizePsiClass(aClass, contextElement, facade.getResolveHelper());
    }
    return qualifiedName;
  }

  private static String normalizePsiClass(PsiClass psiClass, PsiElement contextElement, PsiResolveHelper helper) {
    String name = psiClass.getName();
    PsiClass aClass = helper.resolveReferencedClass(name, contextElement);
    if (psiClass.equals(aClass)) {
      return name;
    }
    PsiClass parentClass = psiClass.getContainingClass();
    if (parentClass != null) {
      return normalizePsiClass(parentClass, contextElement, helper) + "." + name;
    }
    return psiClass.getQualifiedName();
  }
}
