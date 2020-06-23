// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Objects;

/**
 * A helper class to perform the parameter substitution during the Inline method refactoring. 
 * It helps to declare parameters as locals, passing arguments from the call site and then tries to inline the parameters when possible.
 */
class InlineMethodHelper {
  private static final Logger LOG = Logger.getInstance(InlineMethodHelper.class);

  private final @NotNull Project myProject;
  private final @NotNull PsiManager myManager;
  private final @NotNull PsiMethod myMethod;
  private final @NotNull PsiMethod myMethodCopy;
  private final @NotNull PsiElementFactory myFactory;
  private final @NotNull JavaCodeStyleManager myJavaCodeStyle;
  private final @NotNull PsiCallExpression myCall;
  private final @NotNull PsiExpressionList myCallArguments;
  private final @NotNull PsiSubstitutor mySubstitutor;

  InlineMethodHelper(@NotNull Project project, @NotNull PsiMethod method, @NotNull PsiMethod methodCopy, @NotNull PsiCallExpression call) {
    myProject = project;
    myManager = method.getManager();
    myMethod = method;
    myMethodCopy = methodCopy;
    myCall = call;
    myCallArguments = Objects.requireNonNull(call.getArgumentList());
    myFactory = JavaPsiFacade.getElementFactory(myProject);
    myJavaCodeStyle = JavaCodeStyleManager.getInstance(myProject);
    mySubstitutor = createSubstitutor();
  }

  @NotNull
  PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @NotNull
  private PsiSubstitutor createSubstitutor() {
    JavaResolveResult resolveResult = myCall.resolveMethodGenerics();
    if (myMethod.isPhysical()) {
      // Could be specialized
      LOG.assertTrue(myManager.areElementsEquivalent(resolveResult.getElement(), myMethod));
    }
    if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
      Iterator<PsiTypeParameter> oldTypeParameters = PsiUtil.typeParametersIterator(myMethod);
      Iterator<PsiTypeParameter> newTypeParameters = PsiUtil.typeParametersIterator(myMethodCopy);
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      while (newTypeParameters.hasNext()) {
        final PsiTypeParameter newTypeParameter = newTypeParameters.next();
        final PsiTypeParameter oldTypeParameter = oldTypeParameters.next();
        substitutor = substitutor.put(newTypeParameter, resolveResult.getSubstitutor().substitute(oldTypeParameter));
      }
      return substitutor;
    }

    return PsiSubstitutor.EMPTY;
  }

  PsiLocalVariable @NotNull [] declareParameters() {
    PsiCodeBlock block = Objects.requireNonNull(myMethodCopy.getBody());
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(myMethod, mySubstitutor, myCallArguments);
    PsiParameter[] parameters = myMethodCopy.getParameterList().getParameters();
    PsiLocalVariable[] parameterVars = new PsiLocalVariable[parameters.length];
    for (int i = parameters.length - 1; i >= 0; i--) {
      PsiParameter parameter = parameters[i];
      String parameterName = parameter.getName();
      String name = parameterName;
      name = myJavaCodeStyle.variableNameToPropertyName(name, VariableKind.PARAMETER);
      name = myJavaCodeStyle.propertyNameToVariableName(name, VariableKind.LOCAL_VARIABLE);
      if (!name.equals(parameterName)) {
        name = myJavaCodeStyle.suggestUniqueVariableName(name, block.getFirstChild(), true);
      }
      RefactoringUtil.renameVariableReferences(parameter, name, new LocalSearchScope(block), true);
      PsiType paramType = parameter.getType();
      @NonNls String defaultValue;
      if (paramType instanceof PsiEllipsisType) {
        final PsiEllipsisType ellipsisType = (PsiEllipsisType)paramType;
        paramType = mySubstitutor.substitute(ellipsisType.toArrayType());
        if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
          PsiType componentType = ((PsiArrayType)paramType).getComponentType();
          defaultValue = "new " + ObjectUtils.notNull(TypeConversionUtil.erasure(componentType), componentType).getCanonicalText() + "[]{}";
        }
        else {
          defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
        }
      }
      else {
        defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
      }

      PsiExpression initializer = myFactory.createExpressionFromText(defaultValue, null);
      PsiType varType = GenericsUtil.getVariableTypeByExpressionType(mySubstitutor.substitute(paramType));
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(name, varType, initializer);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      parameterVars[i] = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      PsiUtil.setModifierProperty(parameterVars[i], PsiModifier.FINAL, parameter.hasModifierProperty(PsiModifier.FINAL));
    }
    return parameterVars;
  }

  void initializeParameters(PsiLocalVariable[] vars) {
    PsiExpression[] args = myCallArguments.getExpressions();
    if (vars.length > 0) {
      for (int i = 0; i < args.length; i++) {
        int j = Math.min(i, vars.length - 1);
        final PsiExpression initializer = vars[j].getInitializer();
        LOG.assertTrue(initializer != null);
        if (initializer instanceof PsiNewExpression) {
          PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
          if (arrayInitializer != null) { //varargs initializer
            arrayInitializer.add(args[i]);
            continue;
          }
        }

        initializer.replace(args[i]);
      }
    }
  }

  void inlineParameters(PsiLocalVariable[] parmVars) {
    final PsiParameter[] parameters = myMethodCopy.getParameterList().getParameters();
    for (int i = 0; i < parmVars.length; i++) {
      final PsiParameter parameter = parameters[i];
      final boolean strictlyFinal = parameter.hasModifierProperty(PsiModifier.FINAL) && isStrictlyFinal(parameter);
      InlineUtil.tryInlineGeneratedLocal(parmVars[i], strictlyFinal);
    }
  }

  private boolean isStrictlyFinal(PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter, GlobalSearchScope.projectScope(myProject), false)) {
      final PsiElement refElement = reference.getElement();
      final PsiElement anonymousClass = PsiTreeUtil.getParentOfType(refElement, PsiAnonymousClass.class);
      if (anonymousClass != null && PsiTreeUtil.isAncestor(myMethod, anonymousClass, true)) {
        return true;
      }
    }
    return false;
  }
}
