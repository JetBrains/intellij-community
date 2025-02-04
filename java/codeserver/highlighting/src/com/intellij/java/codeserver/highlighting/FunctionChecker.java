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
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Objects.*;


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

  void checkMethodReferenceQualifier(@NotNull PsiMethodReferenceExpression expression) {
    PsiElement referenceNameElement = expression.getReferenceNameElement();
    PsiElement qualifier = expression.getQualifier();
    if (referenceNameElement instanceof PsiKeyword) {
      if (!PsiMethodReferenceUtil.isValidQualifier(expression) && qualifier != null) {
        boolean pending = qualifier instanceof PsiJavaCodeReferenceElement ref &&
                          myVisitor.isIncompleteModel() && IncompleteModelUtil.canBePendingReference(ref);
        if (!pending) {
          myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_QUALIFIER_CLASS_UNRESOLVED.create(qualifier));
        }
      }
    }
    if (qualifier instanceof PsiTypeElement typeElement) {
      PsiType psiType = typeElement.getType();
      if (psiType instanceof PsiClassType) {
        final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
        if (referenceElement != null) {
          PsiType[] typeParameters = referenceElement.getTypeParameters();
          for (PsiType typeParameter : typeParameters) {
            if (typeParameter instanceof PsiWildcardType) {
              myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_QUALIFIER_WILDCARD.create(typeElement));
              break;
            }
          }
        }
      }
    }
  }

  void checkRawConstructorReference(@NotNull PsiMethodReferenceExpression expression) {
    if (expression.isConstructor()) {
      PsiType[] typeParameters = expression.getTypeParameters();
      if (typeParameters.length > 0) {
        PsiElement qualifier = expression.getQualifier();
        if (qualifier instanceof PsiReferenceExpression) {
          PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
          if (resolve instanceof PsiClass && ((PsiClass)resolve).hasTypeParameters()) {
            myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_RAW_CONSTRUCTOR.create(expression));
          }
        }
      }
    }
  }

  void checkMethodReferenceContext(@NotNull PsiMethodReferenceExpression methodRef, @NotNull PsiType functionalInterfaceType) {
    PsiElement resolve = methodRef.resolve();

    if (resolve == null) return;
    PsiClass containingClass = resolve instanceof PsiMethod method ? method.getContainingClass() : (PsiClass)resolve;
    boolean isStaticSelector = PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
    PsiElement qualifier = methodRef.getQualifier();

    boolean isConstructor = true;

    if (resolve instanceof PsiMethod method) {
      boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
      isConstructor = method.isConstructor();

      PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      boolean receiverReferenced = PsiMethodReferenceUtil.isResolvedBySecondSearch(
        methodRef, interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null,
        method.isVarArgs(), isMethodStatic, method.getParameterList().getParametersCount());

      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && qualifier instanceof PsiSuperExpression) {
        myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_ABSTRACT_METHOD.create(methodRef, method));
        return;
      }
      if (!receiverReferenced && isStaticSelector && !isMethodStatic && !isConstructor) {
        if (functionalInterfaceType instanceof PsiClassType classType && classType.hasParameters()) {
          // Prefer surrounding error, as it could be more descriptive
          if (hasSurroundingInferenceError(methodRef)) return;
        }
        myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_NON_STATIC_METHOD_IN_STATIC_CONTEXT.create(methodRef, method));
        return;
      }
      if (!receiverReferenced && !isStaticSelector && isMethodStatic) {
        myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_STATIC_METHOD_NON_STATIC_QUALIFIER.create(methodRef, method));
        return;
      }
      if (receiverReferenced && isStaticSelector && isMethodStatic) {
        myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_STATIC_METHOD_RECEIVER.create(methodRef, method));
        return;
      }
      if (isStaticSelector && isMethodStatic && qualifier instanceof PsiTypeElement) {
        PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(qualifier, PsiJavaCodeReferenceElement.class);
        if (referenceElement != null) {
          PsiReferenceParameterList parameterList = referenceElement.getParameterList();
          if (parameterList != null && parameterList.getTypeArguments().length > 0) {
            myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_PARAMETERIZED_QUALIFIER.create(parameterList));
            return;
          }
        }
      }
    }

    if (isConstructor) {
      if (containingClass != null && PsiUtil.isInnerClass(containingClass) && containingClass.isPhysical()) {
        PsiClass outerClass = containingClass.getContainingClass();
        if (outerClass != null && !InheritanceUtil.hasEnclosingInstanceInScope(outerClass, methodRef, true, false)) {
          myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_ENCLOSING_INSTANCE_NOT_IN_SCOPE.create(methodRef, outerClass));
        }
      }
    }
  }

  void checkParametersCompatible(@NotNull PsiLambdaExpression expression, @Nullable PsiType functionalInterfaceType) {
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod == null) return;
    PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    PsiParameter[] lambdaParameters = expression.getParameterList().getParameters();
    if (lambdaParameters.length != parameters.length) {
      myVisitor.report(JavaErrorKinds.LAMBDA_WRONG_NUMBER_OF_PARAMETERS.create(expression, interfaceMethod));
      return;
    }
    boolean hasFormalParameterTypes = expression.hasFormalParameterTypes();
    PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult);
    for (int i = 0; i < lambdaParameters.length; i++) {
      PsiParameter lambdaParameter = lambdaParameters[i];
      PsiType lambdaParameterType = lambdaParameter.getType();
      PsiType substitutedParamType = substitutor.substitute(parameters[i].getType());
      if (hasFormalParameterTypes &&!PsiTypesUtil.compareTypes(lambdaParameterType, substitutedParamType, true) ||
          !TypeConversionUtil.isAssignable(substitutedParamType, lambdaParameterType)) {
        myVisitor.report(JavaErrorKinds.LAMBDA_INCOMPATIBLE_PARAMETER_TYPES.create(
          lambdaParameter, requireNonNullElse(substitutedParamType, PsiTypes.nullType())));
      }
    }
  }

  private @Nullable JavaCompilationError<?, ?> getFunctionalInterfaceError(
    @NotNull PsiFunctionalExpression context, PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType intersection) {
      Set<MethodSignature> signatures = new HashSet<>();
      Map<PsiType, MethodSignature> typeAndSignature = new HashMap<>();
      for (PsiType type : intersection.getConjuncts()) {
        if (getFunctionalInterfaceError(context, type) == null) {
          MethodSignature signature = LambdaUtil.getFunction(PsiUtil.resolveClassInType(type));
          signatures.add(signature);
          typeAndSignature.put(type, signature);
        }
      }
      PsiType baseType = typeAndSignature.entrySet().iterator().next().getKey();
      MethodSignature baseSignature = typeAndSignature.get(baseType);
      LambdaUtil.TargetMethodContainer baseContainer = LambdaUtil.getTargetMethod(baseType, baseSignature, baseType);
      if (baseContainer == null) {
        return JavaErrorKinds.LAMBDA_NO_TARGET_METHOD.create(context, baseType);
      }
      PsiMethod baseMethod = baseContainer.targetMethod;
      if (signatures.size() > 1) {
        for (Map.Entry<PsiType, MethodSignature> entry : typeAndSignature.entrySet()) {
          if (baseType == entry.getKey()) {
            continue;
          }
          LambdaUtil.TargetMethodContainer container = LambdaUtil.getTargetMethod(entry.getKey(), baseSignature, baseType);
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
        LambdaUtil.TargetMethodContainer container = LambdaUtil.getTargetMethod(type, baseSignature, baseType);
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
      MethodSignature functionalMethod = LambdaUtil.getFunction(aClass);
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
    if (myVisitor.isIncompleteModel() && IncompleteModelUtil.isUnresolvedClassType(functionalInterfaceType)) return null;
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

  void checkLambdaTypeApplicability(@NotNull PsiLambdaExpression expression, 
                                    PsiElement parent, 
                                    @NotNull PsiType functionalInterfaceType) {
    PsiCallExpression callExpression = parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression ?
                                       (PsiCallExpression)parent.getParent() : null;
    MethodCandidateInfo parentCallResolveResult =
      callExpression != null ? ObjectUtils.tryCast(callExpression.resolveMethodGenerics(), MethodCandidateInfo.class) : null;
    String parentInferenceErrorMessage = parentCallResolveResult != null ? parentCallResolveResult.getInferenceErrorMessage() : null;
    PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
    Map<PsiElement, @Nls String> returnErrors = null;
    Set<PsiTypeParameter> parentTypeParameters =
      parentCallResolveResult == null ? Set.of() : Set.of(parentCallResolveResult.getElement().getTypeParameters());
    // If return type of the lambda was not fully inferred and lambda parameters don't mention the same type,
    // it means that lambda is not responsible for inference failure and blaming it would be unreasonable.
    boolean skipReturnCompatibility = parentCallResolveResult != null &&
                                      PsiTypesUtil.mentionsTypeParameters(returnType, parentTypeParameters)
                                      && !FunctionChecker.lambdaParametersMentionTypeParameter(functionalInterfaceType, parentTypeParameters);
    if (!skipReturnCompatibility) {
      returnErrors = LambdaUtil.checkReturnTypeCompatible(expression, returnType);
    }
    if (parentInferenceErrorMessage != null && (returnErrors == null || !returnErrors.containsValue(parentInferenceErrorMessage))) {
      if (returnErrors == null) return;
      checkLambdaInferenceFailure(callExpression, parentCallResolveResult, expression);
    }
    else if (returnErrors != null && !PsiTreeUtil.hasErrorElements(expression)) {
      returnErrors.forEach((expr, message) -> myVisitor.report(JavaErrorKinds.LAMBDA_RETURN_TYPE_ERROR.create(expr, message)));
    }
  }

  private void checkLambdaInferenceFailure(@NotNull PsiCall methodCall,
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

  void checkMethodReferenceReturnType(@NotNull PsiMethodReferenceExpression expression, @NotNull JavaResolveResult result,
                                      @Nullable PsiType functionalInterfaceType) {
    String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
    if (badReturnTypeMessage != null) {
      myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_RETURN_TYPE_ERROR.create(expression, badReturnTypeMessage));
    }
  }

  void checkMethodReferenceResolve(@NotNull PsiMethodReferenceExpression expression,
                                   @NotNull JavaResolveResult @NotNull [] results,
                                   @Nullable PsiType functionalInterfaceType) {
    boolean resolvedButNonApplicable = results.length == 1 && results[0] instanceof MethodCandidateInfo methodInfo &&
                                       !methodInfo.isApplicable() &&
                                       functionalInterfaceType != null;
    if (results.length != 1 || resolvedButNonApplicable) {
      if (results.length == 1 && ((MethodCandidateInfo)results[0]).getInferenceErrorMessage() != null) {
        myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_INFERENCE_ERROR.create(expression, (MethodCandidateInfo)results[0]));
        return;
      }
      if (expression.isConstructor()) {
        PsiClass containingClass = PsiMethodReferenceUtil.getQualifierResolveResult(expression).getContainingClass();

        if (containingClass != null && containingClass.isPhysical()) {
          myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_UNRESOLVED_CONSTRUCTOR.create(expression, containingClass));
        }
      }
      else if (results.length > 1) {
        if (!myVisitor.isIncompleteModel() || !IncompleteModelUtil.isUnresolvedClassType(functionalInterfaceType)) {
          myVisitor.report(JavaErrorKinds.REFERENCE_AMBIGUOUS.create(expression, Arrays.asList(results)));
        }
      }
      else {
        if (myVisitor.isIncompleteModel() && IncompleteModelUtil.canBePendingReference(expression)) {
          PsiElement referenceNameElement = expression.getReferenceNameElement();
          if (referenceNameElement != null) {
            myVisitor.report(JavaErrorKinds.REFERENCE_PENDING.create(referenceNameElement));
          }
        }
        else if (!(resolvedButNonApplicable && hasSurroundingInferenceError(expression))) {
          myVisitor.report(JavaErrorKinds.METHOD_REFERENCE_UNRESOLVED_METHOD.create(expression));
        }
      }
    }
  }
}
