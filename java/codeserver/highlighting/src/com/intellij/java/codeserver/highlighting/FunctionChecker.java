// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.LambdaUtil.getFunction;
import static com.intellij.psi.LambdaUtil.getTargetMethod;

final class FunctionChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  FunctionChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkExtendsSealedClass(@NotNull PsiFunctionalExpression expression, @NotNull PsiType functionalInterfaceType) {
    PsiClass functionalInterface = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceType);
    if (functionalInterface == null || !functionalInterface.hasModifierProperty(PsiModifier.SEALED)) return;
    if (expression instanceof PsiLambdaExpression lambda) {
      myVisitor.report(JavaErrorKinds.LAMBDA_SEALED.create(lambda));
    }
    else if (expression instanceof PsiMethodReferenceExpression methodReference) {
      myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_SEALED.create(methodReference));
    }
  }

  void checkInterfaceFunctional(@NotNull PsiFunctionalExpression context, PsiType functionalInterfaceType) {
    JavaCompilationError<?, ?> error = getFunctionalInterfaceError(context, functionalInterfaceType);
    if (error != null) {
      myVisitor.report(error);
    }
  }

  private static @Nullable JavaCompilationError<?, ?> getFunctionalInterfaceError(
    @NotNull PsiFunctionalExpression context, PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType intersection) {
      Set<MethodSignature> signatures = new HashSet<>();
      Map<PsiType, MethodSignature> typeAndSignature = new HashMap<>();
      for (PsiType type : intersection.getConjuncts()) {
        if (getFunctionalInterfaceError(context, type) == null) {
          MethodSignature signature = getFunction(PsiUtil.resolveClassInType(type));
          signatures.add(signature);
          typeAndSignature.put(type, signature);
        }
      }
      PsiType baseType = typeAndSignature.entrySet().iterator().next().getKey();
      MethodSignature baseSignature = typeAndSignature.get(baseType);
      LambdaUtil.TargetMethodContainer baseContainer = getTargetMethod(baseType, baseSignature, baseType);
      if (baseContainer == null) {
        return JavaErrorKinds.LAMBDA_NO_TARGET_METHOD.create(context, baseType);
      }
      PsiMethod baseMethod = baseContainer.targetMethod;
      if (signatures.size() > 1) {
        for (Map.Entry<PsiType, MethodSignature> entry : typeAndSignature.entrySet()) {
          if (baseType == entry.getKey()) {
            continue;
          }
          LambdaUtil.TargetMethodContainer container = getTargetMethod(entry.getKey(), baseSignature, baseType);
          if (container == null) {
            return JavaErrorKinds.LAMBDA_MULTIPLE_TARGET_METHODS.create(context, functionalInterfaceType);
          }
          if (!LambdaUtil.isLambdaSubsignature(baseMethod, baseType, container.targetMethod, entry.getKey()) ||
              !container.inheritor.hasModifier(JvmModifier.ABSTRACT)) {
            return JavaErrorKinds.LAMBDA_MULTIPLE_TARGET_METHODS.create(context, functionalInterfaceType);
          }
        }
      }
      for (PsiType type : intersection.getConjuncts()) {
        if (typeAndSignature.containsKey(type)) {
          continue;
        }
        LambdaUtil.TargetMethodContainer container = getTargetMethod(type, baseSignature, baseType);
        if (container == null) {
          continue;
        }
        PsiMethod inheritor = container.inheritor;
        PsiMethod target = container.targetMethod;
        if (!inheritor.hasModifier(JvmModifier.ABSTRACT) && LambdaUtil.isLambdaSubsignature(baseMethod, baseType, target, type)) {
          return JavaErrorKinds.LAMBDA_NO_TARGET_METHOD.create(context, functionalInterfaceType);
        }
      }
      return null;
    }
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
      MethodSignature functionalMethod = getFunction(aClass);
      if (functionalMethod != null && functionalMethod.getTypeParameters().length > 0) {
        return JavaErrorKinds.LAMBDA_SAM_GENERIC.create(context);
      }
      return switch (LambdaUtil.checkInterfaceFunctional(aClass)) {
        case VALID -> null;
        case NOT_INTERFACE -> JavaErrorKinds.LAMBDA_TARGET_NOT_INTERFACE.create(context, functionalInterfaceType);
        case NO_ABSTRACT_METHOD -> JavaErrorKinds.LAMBDA_NO_TARGET_METHOD.create(context, functionalInterfaceType);
        case MULTIPLE_ABSTRACT_METHODS -> JavaErrorKinds.LAMBDA_MULTIPLE_TARGET_METHODS.create(context, functionalInterfaceType);
      };
    }
    if (IncompleteModelUtil.isIncompleteModel(context) &&
        IncompleteModelUtil.isUnresolvedClassType(functionalInterfaceType)) {
      return null;
    }
    return JavaErrorKinds.LAMBDA_NOT_FUNCTIONAL_INTERFACE.create(context, functionalInterfaceType);
  }

  private static boolean hasExplicitType(@NotNull PsiParameter parameter) {
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement != null && !typeElement.isInferredType();
  }

  void checkConsistentParameterDeclaration(@NotNull PsiLambdaExpression expression) {
    PsiParameterList parameterList = expression.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length < 2) return;
    boolean hasExplicitParameterTypes = hasExplicitType(parameters[0]);
    for (int i = 1; i < parameters.length; i++) {
      if (hasExplicitParameterTypes != hasExplicitType(parameters[i])) {
        myVisitor.report(JavaErrorKinds.LAMBDA_PARAMETERS_INCONSISTENT_VAR.create(parameterList));
      }
    }
  }

  private static boolean favorParentReport(@NotNull PsiCall methodCall, @NotNull String errorMessage) {
    // Parent resolve failed as well, and it's likely more informative.
    // Suppress this error to allow reporting from parent
    return (errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.failed.to.resolve.argument")) ||
            errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.declaration.for.the.method.reference.not.found"))) &&
           hasSurroundingInferenceError(methodCall);
  }

  static boolean hasSurroundingInferenceError(@NotNull PsiElement context) {
    PsiCall topCall = LambdaUtil.treeWalkUp(context);
    if (topCall == null) return false;
    while (context != topCall) {
      context = context.getParent();
      if (context instanceof PsiMethodCallExpression call &&
          call.resolveMethodGenerics() instanceof MethodCandidateInfo info &&
          info.getInferenceErrorMessage() != null) {
        // Possibly inapplicable method reference due to the surrounding call inference failure:
        // suppress method reference error in order to display more relevant inference error.
        return true;
      }
    }
    return false;
  }

  void checkLambdaInferenceFailure(@NotNull PsiCall methodCall,
                                   @NotNull MethodCandidateInfo resolveResult,
                                   @NotNull PsiLambdaExpression lambdaExpression) {
    String errorMessage = resolveResult.getInferenceErrorMessage();
    if (errorMessage == null) return;
    if (favorParentReport(methodCall, errorMessage)) return;
    PsiMethod method = resolveResult.getElement();
    PsiType expectedTypeByParent = InferenceSession.getTargetTypeByParent(methodCall);
    PsiType actualType =
      methodCall instanceof PsiExpression ? ((PsiExpression)methodCall.copy()).getType() :
      resolveResult.getSubstitutor(false).substitute(method.getReturnType());
    if (expectedTypeByParent != null && actualType != null && !expectedTypeByParent.isAssignableFrom(actualType)) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        methodCall, new JavaIncompatibleTypeErrorContext(expectedTypeByParent, actualType, errorMessage)));
    }
    else {
      myVisitor.report(JavaErrorKinds.LAMBDA_INFERENCE_ERROR.create(lambdaExpression, resolveResult));
    }
  }

  static boolean lambdaParametersMentionTypeParameter(@NotNull PsiType functionalInterfaceType,
                                                      @NotNull Set<? extends PsiTypeParameter> parameters) {
    if (!(functionalInterfaceType instanceof PsiClassType classType)) return false;
    PsiSubstitutor substitutor = classType.resolveGenerics().getSubstitutor();
    PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (method == null) return false;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (PsiTypesUtil.mentionsTypeParameters(substitutor.substitute(parameter.getType()), parameters)) return true;
    }
    return false;
  }
}
