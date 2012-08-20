/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 7/17/12
 */
public class LambdaUtil {
  private static final Logger LOG = Logger.getInstance("#" + LambdaUtil.class.getName());

  @Nullable
  public static PsiType getFunctionalInterfaceReturnType(PsiType functionalInterfaceType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass != null) {
      final MethodSignature methodSignature = getFunction(psiClass);
      if (methodSignature != null) {
        final PsiType returnType = getReturnType(psiClass, methodSignature);
        return resolveResult.getSubstitutor().substitute(returnType);
      }
    }
    return null;
  }

  @Nullable
  public static PsiMethod getFunctionalInterfaceMethod(PsiType functionalInterfaceType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass != null) {
      final MethodSignature methodSignature = getFunction(psiClass);
      if (methodSignature != null) {
        return getMethod(psiClass, methodSignature);
      }
    }
    return null;
  }

  public static boolean isValidLambdaContext(PsiElement context) {
    return context instanceof PsiTypeCastExpression ||
           context instanceof PsiAssignmentExpression ||
           context instanceof PsiVariable ||
           context instanceof PsiLambdaExpression ||
           context instanceof PsiReturnStatement ||
           context instanceof PsiExpressionList ||
           context instanceof PsiParenthesizedExpression;
  }

  public static boolean isLambdaFullyInferred(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    if (expression.getParameterList().getParametersCount() > 0 || getFunctionalInterfaceReturnType(functionalInterfaceType) != PsiType.VOID) {   //todo check that void lambdas without params check
      final Boolean accept = functionalInterfaceType.accept(new TypeParamsChecker(expression));
      return accept == null || !accept.booleanValue();
    }
    return true;
  }

  @Nullable
  public static String checkInterfaceFunctional(PsiType functionalInterfaceType) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceType);
    if (aClass != null) {
      return checkInterfaceFunctional(aClass);
    }
    return null;
  }
  
  @Nullable
  public static String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
    final List<MethodSignature> signatures = findFunctionCandidates(psiClass);
    if (signatures == null) return "Target type of a lambda conversion must be an interface";
    if (signatures.isEmpty()) return "No target method found";
    return signatures.size() == 1 ? null : "Multiple non-overriding abstract methods found";
  }
  
  public static String checkReturnTypeCompatible(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceType) {
    final PsiType returnType = getFunctionalInterfaceReturnType(functionalInterfaceType);
    if (returnType == PsiType.VOID) {
      final PsiElement body = lambdaExpression.getBody();
      if (body instanceof PsiCodeBlock) {
        if (!lambdaExpression.getReturnExpressions().isEmpty()) return "Unexpected return value";
      } else if (body instanceof PsiExpression) {
        final PsiType type = ((PsiExpression)body).getType();
        if (type != PsiType.VOID) {
          return "Incompatible return type " + (type == PsiType.NULL || type == null ? "<null>" : type.getPresentableText()) +" in lambda expression";
        }
      }
    } else if (returnType != null) {
      final List<PsiExpression> returnExpressions = lambdaExpression.getReturnExpressions();
      for (PsiExpression expression : returnExpressions) {
        final PsiType expressionType = expression.getType();
        if (expressionType != null && !returnType.isAssignableFrom(expressionType)) {
          return "Incompatible return type " + expressionType.getPresentableText() + " in lambda expression";
        }
      }
      if (returnExpressions.isEmpty()) {
        return  "Missing return value";
      }
    }
    return null;
  }

  public static boolean isAcceptable(PsiLambdaExpression lambdaExpression, final PsiType leftType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(leftType);
    final PsiClass psiClass = resolveResult.getElement();
    final MethodSignature methodSignature = getFunction(psiClass);
    if (methodSignature == null) return false;
    final PsiParameter[] lambdaParameters = lambdaExpression.getParameterList().getParameters();
    final PsiType[] parameterTypes = methodSignature.getParameterTypes();
    if (lambdaParameters.length != parameterTypes.length) return false;
    for (int lambdaParamIdx = 0, length = lambdaParameters.length; lambdaParamIdx < length; lambdaParamIdx++) {
      PsiParameter parameter = lambdaParameters[lambdaParamIdx];
      final PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement != null) {
        final PsiType lambdaFormalType = typeElement.getType();
        final PsiType methodParameterType = parameterTypes[lambdaParamIdx];
        if (lambdaFormalType instanceof PsiPrimitiveType) {
          if (methodParameterType instanceof PsiPrimitiveType) return methodParameterType.isAssignableFrom(lambdaFormalType);
          return false;
        }

        if (!resolveResult.getSubstitutor().substitute(methodSignature.getSubstitutor().substitute(methodParameterType))
          .isAssignableFrom(lambdaFormalType)) {
          return false;
        }
      }
    }
    LOG.assertTrue(psiClass != null);
    PsiType methodReturnType = getReturnType(psiClass, methodSignature);
    if (methodReturnType != null) {
      if (methodReturnType != PsiType.VOID) {
        methodReturnType = resolveResult.getSubstitutor().substitute(methodSignature.getSubstitutor().substitute(methodReturnType));
        final PsiElement body = lambdaExpression.getBody();
        if (body instanceof PsiCodeBlock) {
          final PsiCodeBlock block = (PsiCodeBlock)body;
          for (PsiStatement statement : block.getStatements()) {
            if (statement instanceof PsiReturnStatement) {
              final PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
              if (returnValue != null) {
                if (!checkReturnTypeAssignability(returnValue.getType(), parameterTypes, lambdaExpression, methodReturnType)) return false;
              }
            }
          }
        }
        else if (body instanceof PsiExpression) {
          return checkReturnTypeAssignability(((PsiExpression)body).getType(), parameterTypes, lambdaExpression, methodReturnType);
        }
      } else {
        final List<PsiExpression> returnExpressions = lambdaExpression.getReturnExpressions();
        for (PsiExpression returnValue : returnExpressions) {
         if (returnValue.getType() != PsiType.VOID) return false;
        }
      }
    }
    return true;
  }

  @Nullable
  private static MethodSignature getFunction(PsiClass psiClass) {
    if (psiClass == null) return null;
    final List<MethodSignature> functions = findFunctionCandidates(psiClass);
    if (functions != null && functions.size() == 1) {
      return functions.get(0);
    }
    return null;
  }


  private static boolean overridesPublicObjectMethod(PsiMethod psiMethod) {
    boolean overrideObject = false;
    for (PsiMethod superMethod : psiMethod.findDeepestSuperMethods()) {
      final PsiClass containingClass = superMethod.getContainingClass();
      if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        if (superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          overrideObject = true;
          break;
        }
      }
    }
    return overrideObject;
  }

  private static MethodSignature getMethodSignature(PsiMethod method, PsiClass psiClass, PsiClass containingClass) {
    final MethodSignature methodSignature;
    if (containingClass != null && containingClass != psiClass) {
      methodSignature = method.getSignature(TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY));
    }
    else {
      methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    }
    return methodSignature;
  }

  @Nullable
  private static List<MethodSignature> hasSubsignature(List<MethodSignature> signatures) {
    for (MethodSignature signature : signatures) {
      boolean subsignature = true;
      for (MethodSignature methodSignature : signatures) {
        if (!signature.equals(methodSignature)) {
          if (!MethodSignatureUtil.isSubsignature(signature, methodSignature)) {
            subsignature = false;
            break;
          }
        }
      }
      if (subsignature) return Collections.singletonList(signature);
    }
    return signatures;
  }

  @Nullable
  private static List<MethodSignature> findFunctionCandidates(PsiClass psiClass) {
    if (psiClass.isInterface()) {
      final List<MethodSignature> methods = new ArrayList<MethodSignature>();
      final PsiMethod[] psiClassMethods = psiClass.getAllMethods();
      for (PsiMethod psiMethod : psiClassMethods) {
        if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
        final PsiClass methodContainingClass = psiMethod.getContainingClass();
        if (!overridesPublicObjectMethod(psiMethod)) {
          methods.add(getMethodSignature(psiMethod, psiClass, methodContainingClass));
        }
      }

      return hasSubsignature(methods);
    }
    return null;
  }


  @Nullable
  private static PsiType getReturnType(PsiClass psiClass, MethodSignature methodSignature) {
    final PsiMethod method = getMethod(psiClass, methodSignature);
    return method != null ? method.getReturnType() : null;
  }

  @Nullable
  private static PsiMethod getMethod(PsiClass psiClass, MethodSignature methodSignature) {
    final PsiMethod[] methodsByName = psiClass.findMethodsByName(methodSignature.getName(), true);
    for (PsiMethod psiMethod : methodsByName) {
      if (MethodSignatureUtil
        .areSignaturesEqual(getMethodSignature(psiMethod, psiClass, psiMethod.getContainingClass()), methodSignature)) {
        return psiMethod;
      }
    }
    return null;
  }

  private static boolean checkReturnTypeAssignability(PsiType lambdaReturnType,
                                                      PsiType[] parameterTypes,
                                                      PsiLambdaExpression lambdaExpression,
                                                      PsiType methodReturnType) {
    if (lambdaReturnType instanceof PsiLambdaParameterType) {
      final PsiParameter parameter = ((PsiLambdaParameterType)lambdaReturnType).getParameter();
      final int parameterIndex = lambdaExpression.getParameterList().getParameterIndex(parameter);
      if (parameterIndex > -1) {
        lambdaReturnType = parameterTypes[parameterIndex];
      }
    }
    return lambdaReturnType != null && methodReturnType.isAssignableFrom(lambdaReturnType);
  }

  public static int getLambdaIdx(PsiExpressionList expressionList, final PsiLambdaExpression element) {
    PsiExpression[] expressions = expressionList.getExpressions();
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      if (PsiTreeUtil.isAncestor(expression, element, false)) {
        return i;
      }
    }
    return -1;
  }

  public static boolean dependsOnTypeParams(PsiType type, PsiLambdaExpression expr) {
    final Boolean accept = type.accept(new TypeParamsChecker(expr));
    return accept != null && accept.booleanValue();
  }

  public static boolean isFreeFromTypeInferenceArgs(final PsiParameter[] methodParameters,
                                                    final PsiLambdaExpression lambdaExpression,
                                                    final PsiExpression expression) {
    final PsiParameter[] lambdaParams = lambdaExpression.getParameterList().getParameters(); 
    final boolean [] independent = new boolean[]{true};
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        int usedParamIdx = -1;
        for (int i = 0; i < lambdaParams.length; i++) {
          PsiParameter param = lambdaParams[i];
          if (expression.isReferenceTo(param)) {
            usedParamIdx = i;
            break;
          }
        }

        if (usedParamIdx > -1 && dependsOnTypeParams(methodParameters[usedParamIdx].getType(), lambdaExpression)) {
          independent[0] = false;
        }
      }
    });
    return independent[0];
  }

  private static class TypeParamsChecker extends PsiTypeVisitor<Boolean> {
    private final PsiLambdaExpression myExpression;

    public TypeParamsChecker(PsiLambdaExpression expression) {
      myExpression = expression;
    }

    @Override
    public Boolean visitClassType(PsiClassType classType) {
      for (PsiType paramType : classType.getParameters()) {
        final Boolean paramAccepted = paramType.accept(this);
        if (paramAccepted != null && paramAccepted.booleanValue()) return true;
      }
      final PsiClass resolve = classType.resolve();
      if (resolve instanceof PsiTypeParameter) {
        if (!PsiTreeUtil.isAncestor(((PsiTypeParameter)resolve).getOwner(), myExpression, false)) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    @Override
    public Boolean visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound != null) return bound.accept(this);
      return false;
    }

    @Nullable
    @Override
    public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
      return visitWildcardType(capturedWildcardType.getWildcard());
    }

    @Nullable
    @Override
    public Boolean visitLambdaExpressionType(PsiLambdaExpressionType lambdaExpressionType) {
      return true;
    }

    @Nullable
    @Override
    public Boolean visitArrayType(PsiArrayType arrayType) {
      return arrayType.getComponentType().accept(this);
    }

    @Override
    public Boolean visitType(PsiType type) {
      return false;
    }
  }
}
