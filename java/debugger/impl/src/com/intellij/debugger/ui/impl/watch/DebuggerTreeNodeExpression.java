// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class DebuggerTreeNodeExpression {
  public static @Nullable PsiExpression substituteThis(@Nullable PsiElement expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue)
    throws EvaluateException {
    return substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue, null);
  }

  public static @Nullable PsiExpression substituteThis(@Nullable PsiElement expressionWithThis,
                                                       PsiExpression howToEvaluateThis,
                                                       Value howToEvaluateThisValue,
                                                       @Nullable String howToEvaluateThisDeclaredType)
    throws EvaluateException {
    if (!(expressionWithThis instanceof PsiExpression)) return null;
    PsiExpression result = (PsiExpression)expressionWithThis.copy();

    PsiClass thisClass = PsiTreeUtil.getContextOfType(result, PsiClass.class, true);

    if (thisClass instanceof PsiAnonymousClass aClass) {
      // ChangeContextUtil.encodeContextInfo skips PsiAnonymousClass,
      // so re-create expression with named base class context for proper this-substitution
      PsiClass baseClass = aClass.getBaseClassType().resolve();
      if (baseClass != null) {
        result = JavaPsiFacade.getElementFactory(result.getProject()).createExpressionFromText(result.getText(), baseClass);
        thisClass = baseClass;
      }
    }

    boolean castNeeded = true;

    if (howToEvaluateThisValue instanceof ObjectReference reference &&
        reference.referenceType().name().equals(howToEvaluateThisDeclaredType)) {
      // The declared type is the runtime type, no cast is needed.
      castNeeded = false;
    }
    else if (thisClass != null) {
      PsiType type = howToEvaluateThis.getType();
      if (type == null && howToEvaluateThisDeclaredType != null) {
        // If Java resolver cannot get the type of the expression,
        // try to find the type by ValueDescriptorImpl#getDeclaredType obtained from JDI.
        type = DebuggerUtils.getType(howToEvaluateThisDeclaredType, howToEvaluateThis.getProject());
      }
      if (type != null) {
        if (type instanceof PsiClassType classType) {
          PsiClass psiClass = classType.resolve();
          if (psiClass != null && (psiClass.isEquivalentTo(thisClass) || psiClass.isInheritor(thisClass, true))) {
            castNeeded = false;
          }
        }
        else if (type instanceof PsiArrayType && PsiUtil.isArrayClass(thisClass)) {
          castNeeded = false;
        }
      }
    }

    if (castNeeded) {
      if (thisClass != null && isObjectWithoutPsiClass(howToEvaluateThisValue, howToEvaluateThis.getProject())) {
        // If the object does not have a PSI class, we cannot cast it to the runtime type.
        // Instead, we cast to `thisClass`.
        howToEvaluateThis = castToType(howToEvaluateThis, PsiTypesUtil.getClassType(thisClass));
      }
      else {
        howToEvaluateThis = castToRuntimeType(howToEvaluateThis, howToEvaluateThisValue);
      }
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

  /**
   * Returns {@code true} if {@code value} is an object whose runtime class has no PSI class.
   * An example is a Kotlin anonymous object.
   */
  private static boolean isObjectWithoutPsiClass(Value value, Project project) {
    return value instanceof ObjectReference reference &&
           DebuggerUtils.findClass(reference.referenceType().name(), project, GlobalSearchScope.allScope(project)) == null;
  }

  public static final Key<Set<String>> ADDITIONAL_IMPORTS_KEY = Key.create("ADDITIONAL_IMPORTS");

  public static PsiExpression castToRuntimeType(PsiExpression expression, Value value) throws EvaluateException {
    if (!(value instanceof ObjectReference reference)) {
      return expression;
    }

    ReferenceType valueType = reference.referenceType();
    if (valueType == null) {
      return expression;
    }

    Project project = expression.getProject();

    PsiType type = RuntimeTypeEvaluator.getCastableRuntimeType(project, value);
    if (type == null) {
      return expression;
    }

    return castToType(expression, type);
  }

  private static PsiExpression castToType(PsiExpression expression, PsiType type) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
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
