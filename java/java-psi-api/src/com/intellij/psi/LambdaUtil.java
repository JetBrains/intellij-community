/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: 7/17/12
 */
public class LambdaUtil {
  private static final Logger LOG = Logger.getInstance("#" + LambdaUtil.class.getName());
  @NonNls public static final String JAVA_LANG_FUNCTIONAL_INTERFACE = "java.lang.FunctionalInterface";

  @Nullable
  public static PsiType getFunctionalInterfaceReturnType(PsiLambdaExpression expr) {
    return getFunctionalInterfaceReturnType(expr.getFunctionalInterfaceType());
  }

  @Nullable
  public static PsiType getFunctionalInterfaceReturnType(@Nullable PsiType functionalInterfaceType) {
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
  public static PsiMethod getFunctionalInterfaceMethod(@Nullable PsiType functionalInterfaceType) {
    return getFunctionalInterfaceMethod(PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
  }

  @Nullable
  public static PsiMethod getFunctionalInterfaceMethod(PsiClassType.ClassResolveResult result) {
    final PsiClass psiClass = result.getElement();
    if (psiClass != null) {
      final MethodSignature methodSignature = getFunction(psiClass);
      if (methodSignature != null) {
        return getMethod(psiClass, methodSignature);
      }
    }
    return null;
  }

  public static PsiSubstitutor getSubstitutor(@NotNull PsiMethod method, @NotNull PsiClassType.ClassResolveResult resolveResult) {
    final PsiClass derivedClass = resolveResult.getElement();
    LOG.assertTrue(derivedClass != null);

    final PsiClass methodContainingClass = method.getContainingClass();
    LOG.assertTrue(methodContainingClass != null);
    PsiSubstitutor initialSubst = resolveResult.getSubstitutor();
    final PsiSubstitutor superClassSubstitutor =
      TypeConversionUtil.getSuperClassSubstitutor(methodContainingClass, derivedClass, PsiSubstitutor.EMPTY);
    for (PsiTypeParameter param : superClassSubstitutor.getSubstitutionMap().keySet()) {
      final PsiType substitute = superClassSubstitutor.substitute(param);
      if (substitute != null) {
        initialSubst = initialSubst.put(param, initialSubst.substitute(substitute));
      }
    }
    return initialSubst;
  }

  public static boolean isValidLambdaContext(@Nullable PsiElement context) {
    return context instanceof PsiTypeCastExpression ||
           context instanceof PsiAssignmentExpression ||
           context instanceof PsiVariable ||
           context instanceof PsiLambdaExpression ||
           context instanceof PsiReturnStatement ||
           context instanceof PsiExpressionList ||
           context instanceof PsiParenthesizedExpression ||
           context instanceof PsiArrayInitializerExpression ||
           context instanceof PsiConditionalExpression;
  }

  public static boolean isLambdaFullyInferred(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    final boolean hasParams = expression.getParameterList().getParametersCount() > 0;
    if (hasParams || getFunctionalInterfaceReturnType(functionalInterfaceType) != PsiType.VOID) {   //todo check that void lambdas without params check
      
      return !dependsOnTypeParams(functionalInterfaceType, functionalInterfaceType, expression);
    }
    return true;
  }

  private static boolean checkRawAcceptable(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethodCallExpression) {
        final PsiExpression qualifierExpression = ((PsiMethodCallExpression)gParent).getMethodExpression().getQualifierExpression();
        final PsiType type = qualifierExpression != null ? qualifierExpression.getType() : null;
        if (type instanceof PsiClassType && ((PsiClassType)type).isRaw()) {
          return true;
        }
        final PsiMethod method = ((PsiMethodCallExpression)gParent).resolveMethod();
        if (method != null) {
          int lambdaIdx = getLambdaIdx((PsiExpressionList)parent, expression);
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          final PsiType normalizedType = getNormalizedType(parameters[adjustLambdaIdx(lambdaIdx, method, parameters)]);
          if (normalizedType instanceof PsiClassType && ((PsiClassType)normalizedType).isRaw()) return true;
        }
      }
      if (functionalInterfaceType instanceof PsiClassType && ((PsiClassType)functionalInterfaceType).isRaw()){
        return false;
      }
    }
    return true;
  }

  public static boolean isAcceptable(PsiLambdaExpression lambdaExpression, final PsiType leftType, boolean checkReturnType) {
    if (leftType instanceof PsiIntersectionType) {
      for (PsiType conjunctType : ((PsiIntersectionType)leftType).getConjuncts()) {
        if (isAcceptable(lambdaExpression, conjunctType, checkReturnType)) return true;
      }
      return false;
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(GenericsUtil.eliminateWildcards(leftType));
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass instanceof PsiAnonymousClass) {
      return isAcceptable(lambdaExpression, ((PsiAnonymousClass)psiClass).getBaseClassType(), checkReturnType);
    }
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
          if (methodParameterType instanceof PsiPrimitiveType) return methodParameterType.equals(lambdaFormalType);
          return false;
        }

        if (!TypeConversionUtil.erasure(lambdaFormalType)
          .isAssignableFrom(TypeConversionUtil.erasure(GenericsUtil.eliminateWildcards(
            resolveResult.getSubstitutor().substitute(methodSignature.getSubstitutor().substitute(methodParameterType)))))) {
          return false;
        }
      }
    }
    if (checkReturnType) {
      final String uniqueVarName =
        JavaCodeStyleManager.getInstance(lambdaExpression.getProject()).suggestUniqueVariableName("l", lambdaExpression, true);
      String canonicalText = leftType.getCanonicalText();
      if (leftType instanceof PsiEllipsisType) {
        canonicalText = ((PsiEllipsisType)leftType).toArrayType().getCanonicalText();
      }
      final PsiStatement assignmentFromText = JavaPsiFacade.getElementFactory(lambdaExpression.getProject())
        .createStatementFromText(canonicalText + " " + uniqueVarName + " = " + lambdaExpression.getText(), lambdaExpression);
      final PsiLocalVariable localVariable = (PsiLocalVariable)((PsiDeclarationStatement)assignmentFromText).getDeclaredElements()[0];
      LOG.assertTrue(psiClass != null);
      PsiType methodReturnType = getReturnType(psiClass, methodSignature);
      if (methodReturnType != null) {
        methodReturnType = resolveResult.getSubstitutor().substitute(methodSignature.getSubstitutor().substitute(methodReturnType));
        return LambdaHighlightingUtil.checkReturnTypeCompatible((PsiLambdaExpression)localVariable.getInitializer(), methodReturnType) == null;
      }
    }
    return true;
  }

  @Nullable
  static MethodSignature getFunction(PsiClass psiClass) {
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
  static List<MethodSignature> findFunctionCandidates(PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      psiClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)psiClass).getBaseClassType());
    }
    if (psiClass != null && psiClass.isInterface()) {
      final List<MethodSignature> methods = new ArrayList<MethodSignature>();
      final Collection<HierarchicalMethodSignature> visibleSignatures = psiClass.getVisibleSignatures();
      for (HierarchicalMethodSignature signature : visibleSignatures) {
        final PsiMethod psiMethod = signature.getMethod();
        if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (!overridesPublicObjectMethod(psiMethod)) {
          methods.add(signature);
        }
      }

      return hasSubsignature(methods);
    }
    return null;
  }


  @Nullable
  private static PsiType getReturnType(PsiClass psiClass, MethodSignature methodSignature) {
    final PsiMethod method = getMethod(psiClass, methodSignature);
    if (method != null) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;
      return TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY).substitute(method.getReturnType());
    }
    else {
      return null;
    }
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

  public static int getLambdaIdx(PsiExpressionList expressionList, final PsiElement element) {
    PsiExpression[] expressions = expressionList.getExpressions();
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      if (PsiTreeUtil.isAncestor(expression, element, false)) {
        return i;
      }
    }
    return -1;
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiLambdaExpression expr,
                                            PsiTypeParameter param2Check) {
    return depends(type, new TypeParamsChecker(expr), param2Check);
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiType functionalInterfaceType,
                                            PsiElement lambdaExpression,
                                            PsiTypeParameter... param2Check) {
    return depends(type, new TypeParamsChecker(lambdaExpression,
                                               PsiUtil.resolveClassInType(functionalInterfaceType)), param2Check);
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiClass aClass,
                                            PsiMethod aMethod) {
    return depends(type, new TypeParamsChecker(aMethod, aClass));
  }

  static boolean depends(PsiType type, TypeParamsChecker visitor, PsiTypeParameter... param2Check) {
    if (!visitor.startedInference()) return false;
    final Boolean accept = type.accept(visitor);
    if (param2Check.length > 0) {
      return visitor.used(param2Check);
    }
    return accept != null && accept.booleanValue();
  }

  public static boolean isFreeFromTypeInferenceArgs(final PsiParameter[] methodParameters,
                                                    final PsiLambdaExpression lambdaExpression,
                                                    final PsiExpression expression,
                                                    final PsiSubstitutor subst,
                                                    final PsiType functionalInterfaceType,
                                                    final PsiTypeParameter typeParam) {
    if (expression instanceof PsiCallExpression && ((PsiCallExpression)expression).getTypeArguments().length > 0) return true;
    if (expression instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)expression).getClassOrAnonymousClassReference();
      if (classReference != null) {
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
          if (typeParameterElements.length > 0) {
            if (!(typeParameterElements[0].getType() instanceof PsiDiamondType)) {
              return true;
            }
          }
        }
      }
    }
    final PsiParameter[] lambdaParams = lambdaExpression.getParameterList().getParameters();
    if (lambdaParams.length != methodParameters.length) return false;
    final boolean[] independent = new boolean[]{true};
    final PsiMethod interfaceMethod = getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) return false;
    final TypeParamsChecker paramsChecker = new TypeParamsChecker(lambdaExpression);
    for (PsiParameter parameter : interfaceMethod.getParameterList().getParameters()) {
      subst.substitute(parameter.getType()).accept(paramsChecker);
    }
    paramsChecker.myUsedTypeParams.add(typeParam);

    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        final PsiExpression thenExpression = expression.getThenExpression();
        if (thenExpression != null) {
          thenExpression.accept(this);
        }
        final PsiExpression elseExpression = expression.getElseExpression();
        if (elseExpression != null) {
          elseExpression.accept(this);
        }
      }

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

        if (usedParamIdx > -1 && dependsOnTypeParams(subst.substitute(methodParameters[usedParamIdx].getType()), functionalInterfaceType,
                                                     lambdaExpression, paramsChecker.myUsedTypeParams.toArray(new PsiTypeParameter[paramsChecker.myUsedTypeParams.size()]))) {
          independent[0] = false;
        }
      }
    });
    return independent[0];
  }

  @Nullable
  public static PsiType getFunctionalInterfaceType(PsiElement expression, final boolean tryToSubstitute) {
    return getFunctionalInterfaceType(expression, tryToSubstitute, -1);
  }

  @Nullable
  public static PsiType getFunctionalInterfaceType(PsiElement expression, final boolean tryToSubstitute, int paramIdx) {
    PsiElement parent = expression.getParent();
    PsiElement element = expression;
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiConditionalExpression) {
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getThenExpression() != element &&
          ((PsiConditionalExpression)parent).getElseExpression() != element) break;
      element = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiArrayInitializerExpression) {
      final PsiType psiType = ((PsiArrayInitializerExpression)parent).getType();
      if (psiType instanceof PsiArrayType) {
        return ((PsiArrayType)psiType).getComponentType();
      }
    } else if (parent instanceof PsiTypeCastExpression) {
      final PsiType castType = ((PsiTypeCastExpression)parent).getType();
      if (castType instanceof PsiIntersectionType) {
        for (PsiType conjunctType : ((PsiIntersectionType)castType).getConjuncts()) {
          if (getFunctionalInterfaceMethod(conjunctType) != null) return conjunctType;
        }
      }
      return castType;
    }
    else if (parent instanceof PsiVariable) {
      return ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression && expression instanceof PsiExpression && !PsiUtil.isOnAssignmentLeftHand((PsiExpression)expression)) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      return lExpression.getType();
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final int lambdaIdx = getLambdaIdx(expressionList, expression);
      if (lambdaIdx > -1) {

        PsiType cachedType = null;
        final Pair<PsiMethod, PsiSubstitutor> method = MethodCandidateInfo.getCurrentMethod(parent);
        if (method != null) {
          final PsiParameter[] parameters = method.first.getParameterList().getParameters();
          cachedType = lambdaIdx < parameters.length ? method.second.substitute(getNormalizedType(parameters[adjustLambdaIdx(lambdaIdx, method.first, parameters)])) : null;
          if (!tryToSubstitute) return cachedType;
        }

        final PsiElement gParent = expressionList.getParent();
        if (gParent instanceof PsiCall) {
          final PsiCall contextCall = (PsiCall)gParent;
          final JavaResolveResult resolveResult = contextCall.resolveMethodGenerics();
            final PsiElement resolve = resolveResult.getElement();
            if (resolve instanceof PsiMethod) {
              final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
              final int finalLambdaIdx = adjustLambdaIdx(lambdaIdx, (PsiMethod)resolve, parameters);
              if (finalLambdaIdx < parameters.length) {
                if (!tryToSubstitute) return getNormalizedType(parameters[finalLambdaIdx]);
                if (cachedType != null && paramIdx > -1) {
                  final PsiMethod interfaceMethod = getFunctionalInterfaceMethod(cachedType);
                  if (interfaceMethod != null) {
                    final PsiClassType.ClassResolveResult cachedResult = PsiUtil.resolveGenericsClassInType(cachedType);
                    final PsiType interfaceMethodParameterType = interfaceMethod.getParameterList().getParameters()[paramIdx].getType();
                    if (!dependsOnTypeParams(cachedResult.getSubstitutor().substitute(interfaceMethodParameterType), cachedType, expression)){
                      return cachedType;
                    }
                  }
                }
                return PsiResolveHelper.ourGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
                  @Override
                  public PsiType compute() {
                    return resolveResult.getSubstitutor().substitute(getNormalizedType(parameters[finalLambdaIdx]));
                  }
                });
              }
            }
            return null;
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiLambdaExpression gParent = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
      if (gParent != null) {
        return getFunctionalInterfaceTypeByContainingLambda(gParent);
      } else {
        final PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
        if (method != null) {
          return method.getReturnType();
        }
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      return getFunctionalInterfaceTypeByContainingLambda((PsiLambdaExpression)parent);
    }
    return null;
  }

  private static PsiType getFunctionalInterfaceTypeByContainingLambda(@NotNull PsiLambdaExpression parentLambda) {
    final PsiType parentInterfaceType = parentLambda.getFunctionalInterfaceType();
    return parentInterfaceType != null ? getFunctionalInterfaceReturnType(parentInterfaceType) : null;
  }

  private static int adjustLambdaIdx(int lambdaIdx, PsiMethod resolve, PsiParameter[] parameters) {
    final int finalLambdaIdx;
    if (resolve.isVarArgs() && lambdaIdx >= parameters.length) {
      finalLambdaIdx = parameters.length - 1;
    } else {
      finalLambdaIdx = lambdaIdx;
    }
    return finalLambdaIdx;
  }

  private static PsiType getNormalizedType(PsiParameter parameter) {
    final PsiType type = parameter.getType();
    if (type instanceof PsiEllipsisType) {
      return ((PsiEllipsisType)type).getComponentType();
    }
    return type;
  }

  public static PsiType getLambdaParameterType(PsiParameter param) {
    final PsiElement paramParent = param.getParent();
    if (paramParent instanceof PsiParameterList) {
      final int parameterIndex = ((PsiParameterList)paramParent).getParameterIndex(param);
      if (parameterIndex > -1) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param, PsiLambdaExpression.class);
        if (lambdaExpression != null) {

          PsiType type = getFunctionalInterfaceType(lambdaExpression, true, parameterIndex);
          if (type == null) {
            type = getFunctionalInterfaceType(lambdaExpression, false);
          }
          if (type instanceof PsiIntersectionType) {
            final PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
            for (PsiType conjunct : conjuncts) {
              final PsiType lambdaParameterFromType = getLambdaParameterFromType(parameterIndex, lambdaExpression, conjunct);
              if (lambdaParameterFromType != null) return lambdaParameterFromType;
            }
          } else {
            final PsiType lambdaParameterFromType = getLambdaParameterFromType(parameterIndex, lambdaExpression, type);
            if (lambdaParameterFromType != null) {
              return lambdaParameterFromType;
            }
          }
        }
      }
    }
    return new PsiLambdaParameterType(param);
  }

  private static PsiType getLambdaParameterFromType(int parameterIndex, PsiLambdaExpression lambdaExpression, PsiType conjunct) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(conjunct);
    if (resolveResult != null) {
      final PsiMethod method = getFunctionalInterfaceMethod(conjunct);
      if (method != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameterIndex < parameters.length) {
          final PsiType psiType = getSubstitutor(method, resolveResult).substitute(parameters[parameterIndex].getType());
          if (!dependsOnTypeParams(psiType, conjunct, lambdaExpression)) {
            return GenericsUtil.eliminateWildcards(psiType);
          }
        }
      }
    }
    return null;
  }

  public static PsiSubstitutor inferFromReturnType(final PsiTypeParameter[] typeParameters,
                                                   final PsiType returnType,
                                                   @Nullable final PsiType interfaceMethodReturnType,
                                                   PsiSubstitutor psiSubstitutor,
                                                   final LanguageLevel languageLevel,
                                                   final Project project) {
    if (interfaceMethodReturnType == null) return psiSubstitutor;
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    for (PsiTypeParameter typeParameter : typeParameters) {
      final PsiType constraint = resolveHelper.getSubstitutionForTypeParameter(typeParameter, returnType, interfaceMethodReturnType, false, languageLevel);
      if (constraint != PsiType.NULL && constraint != null) {
        PsiType inferredType = null;
        final PsiClassType[] bounds = typeParameter.getExtendsListTypes();
        for (PsiClassType classTypeBound : bounds) {
          if (TypeConversionUtil.isAssignable(classTypeBound, constraint)) {
            inferredType = constraint;
            break;
          }
        }
        if (bounds.length == 0) {
          inferredType = constraint;
        }
        if (inferredType != null) {
          psiSubstitutor = psiSubstitutor.put(typeParameter, inferredType);
        }
      }
    }
    return psiSubstitutor;
  }

  public static boolean notInferredType(PsiType typeByExpression) {
    return typeByExpression instanceof PsiMethodReferenceType || typeByExpression instanceof PsiLambdaExpressionType || typeByExpression instanceof PsiLambdaParameterType;
  }

  public static List<PsiReturnStatement> getReturnStatements(PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    final List<PsiReturnStatement> result = new ArrayList<PsiReturnStatement>();
    if (body != null) {
      body.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
          result.add(statement);
        }

        @Override
        public void visitClass(PsiClass aClass) {
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
        }
      });
    }
    return result;
  }

  public static List<PsiExpression> getReturnExpressions(PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression) {
      //if (((PsiExpression)body).getType() != PsiType.VOID) return Collections.emptyList();
      return Collections.singletonList((PsiExpression)body);
    }
    final List<PsiExpression> result = new ArrayList<PsiExpression>();
    for (PsiReturnStatement returnStatement : getReturnStatements(lambdaExpression)) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        result.add(returnValue);
      }
    }
    return result;
  }

  @Nullable
  public static String checkFunctionalInterface(@NotNull PsiAnnotation annotation, @NotNull LanguageLevel languageLevel) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && Comparing.strEqual(annotation.getQualifiedName(), JAVA_LANG_FUNCTIONAL_INTERFACE)) {
      final PsiAnnotationOwner owner = annotation.getOwner();
      if (owner instanceof PsiModifierList) {
        final PsiElement parent = ((PsiModifierList)owner).getParent();
        if (parent instanceof PsiClass) {
          return LambdaHighlightingUtil.checkInterfaceFunctional((PsiClass)parent, ((PsiClass)parent).getName() + " is not a functional interface");
        }
      }
    }
    return null;
  }

  public static boolean isValidQualifier4InterfaceStaticMethodCall(@NotNull PsiMethod method,
                                                                   @NotNull PsiReferenceExpression methodReferenceExpression,
                                                                   @NotNull LanguageLevel languageLevel) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
        return qualifierExpression == null && PsiTreeUtil.isAncestor(containingClass, methodReferenceExpression, true)||
               qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() == containingClass;
      }
    }
    return true;
  }

  static class TypeParamsChecker extends PsiTypeVisitor<Boolean> {
    private PsiMethod myMethod;
    private final PsiClass myClass;
    private final Set<PsiTypeParameter> myUsedTypeParams = new HashSet<PsiTypeParameter>();

    private TypeParamsChecker(PsiMethod method, PsiClass aClass) {
      myMethod = method;
      myClass = aClass;
    }

    public TypeParamsChecker(PsiElement expression) {
      this(expression, PsiUtil.resolveGenericsClassInType(getFunctionalInterfaceType(expression, false)).getElement());
    }

    public TypeParamsChecker(PsiElement expression, PsiClass aClass) {
      myClass = aClass;
      PsiElement parent = expression != null ? expression.getParent() : null;
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiExpressionList) {
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiCall) {
          final Pair<PsiMethod, PsiSubstitutor> pair = MethodCandidateInfo.getCurrentMethod(parent);
          myMethod = pair != null ? pair.first : null;
          if (myMethod == null) {
            myMethod = ((PsiCall)gParent).resolveMethod();
          }
          if (myMethod != null && PsiTreeUtil.isAncestor(myMethod, expression, false)) {
            myMethod = null;
          }
        }
      }
    }

    public boolean startedInference() {
      return myMethod != null;
    }

    @Override
    public Boolean visitClassType(PsiClassType classType) {
      boolean used = false;
      for (PsiType paramType : classType.getParameters()) {
        final Boolean paramAccepted = paramType.accept(this);
        used |= paramAccepted != null && paramAccepted.booleanValue();
      }
      final PsiClass resolve = classType.resolve();
      if (resolve instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)resolve;
        if (check(typeParameter)) {
          myUsedTypeParams.add(typeParameter);
          return true;
        }
      }
      return used;
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

    private boolean check(PsiTypeParameter check) {
      final PsiTypeParameterListOwner owner = check.getOwner();
      if (owner == myMethod) {
        return true;
      }
      else if (owner == myClass) {
        return true;
      }
      return false;
    }

    public boolean used(PsiTypeParameter... parameters) {
      for (PsiTypeParameter parameter : parameters) {
        if (myUsedTypeParams.contains(parameter)) return true;
      }
      return false;
    }
  }
}
