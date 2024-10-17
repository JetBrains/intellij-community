// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
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
    PsiSubstitutor origSubstitutor = resolveResult.getSubstitutor();
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (substitutor != PsiSubstitutor.EMPTY) {
      if (myMethod.isPhysical()) { // Could be specialized, thus non-physical, see InlineMethodSpecialization
        PsiMethod calledMethod = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
        if (calledMethod != null && !myManager.areElementsEquivalent(calledMethod, myMethod)) {
          // Could be an implementation method
          PsiSubstitutor superSubstitutor =
            TypeConversionUtil.getSuperClassSubstitutor(Objects.requireNonNull(calledMethod.getContainingClass()),
                                                        Objects.requireNonNull(myMethod.getContainingClass()),
                                                        PsiSubstitutor.EMPTY);
          PsiSubstitutor superMethodSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(
            myMethod.getHierarchicalMethodSignature(), calledMethod.getHierarchicalMethodSignature());
          if (superMethodSubstitutor != null) {
            superSubstitutor = superSubstitutor.putAll(superMethodSubstitutor);
          }
          for (Map.Entry<PsiTypeParameter, PsiType> entry : superSubstitutor.getSubstitutionMap().entrySet()) {
            PsiTypeParameter parameter = entry.getKey();
            PsiType type = entry.getValue();
            if (type instanceof PsiClassType classType && classType.resolve() instanceof PsiTypeParameter typeParameter) {
              substitutor = substitutor.put(typeParameter, origSubstitutor.substitute(parameter));
            }
          }
          origSubstitutor = substitutor;
        }
      }
      Iterator<PsiTypeParameter> oldTypeParameters = PsiUtil.typeParametersIterator(myMethod);
      Iterator<PsiTypeParameter> newTypeParameters = PsiUtil.typeParametersIterator(myMethodCopy);
      while (newTypeParameters.hasNext()) {
        final PsiTypeParameter newTypeParameter = newTypeParameters.next();
        final PsiTypeParameter oldTypeParameter = oldTypeParameters.next();
        substitutor = substitutor.put(newTypeParameter, origSubstitutor.substitute(oldTypeParameter));
      }
    }
    return substitutor;
  }

  PsiLocalVariable @NotNull [] declareParameters() {
    PsiCodeBlock block = Objects.requireNonNull(myMethodCopy.getBody());
    boolean compactConstructor = JavaPsiRecordUtil.isCompactConstructor(myMethodCopy);
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(myMethod, mySubstitutor, myCallArguments);
    PsiParameter[] parameters = myMethod.getParameterList().getParameters();
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
      if (paramType instanceof PsiEllipsisType ellipsisType) {
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
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(name, paramType, initializer);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      parameterVars[i] = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      PsiUtil.setModifierProperty(parameterVars[i], PsiModifier.FINAL, parameter.hasModifierProperty(PsiModifier.FINAL));
      if (compactConstructor) {
        block.add(myFactory.createStatementFromText("this." + name + '=' + name + ';', myMethod));
      }
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
        if (initializer instanceof PsiNewExpression newExpression) {
          PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
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
    final PsiParameter[] parameters = (myMethod.isValid() ? myMethod : myMethodCopy).getParameterList().getParameters();
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

  public void substituteTypes(PsiLocalVariable[] vars) {
    for (PsiLocalVariable var : vars) {
      PsiType newType = GenericsUtil.getVariableTypeByExpressionType(mySubstitutor.substitute(var.getType()));
      var.getTypeElement().replace(JavaPsiFacade.getElementFactory(myProject).createTypeElement(newType));
    }
  }
}
