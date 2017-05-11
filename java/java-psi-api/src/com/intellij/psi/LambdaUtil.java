/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author anna
 * @since 17.07.2012
 */
public class LambdaUtil {
  public static final RecursionGuard ourParameterGuard = RecursionManager.createGuard("lambdaParameterGuard");
  public static final ThreadLocal<Map<PsiElement, PsiType>> ourFunctionTypes = new ThreadLocal<>();
  private static final Logger LOG = Logger.getInstance(LambdaUtil.class);

  @Nullable
  public static PsiType getFunctionalInterfaceReturnType(PsiFunctionalExpression expr) {
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

  @Contract("null -> null")
  @Nullable
  public static PsiMethod getFunctionalInterfaceMethod(@Nullable PsiType functionalInterfaceType) {
    return getFunctionalInterfaceMethod(PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
  }

  public static PsiMethod getFunctionalInterfaceMethod(@Nullable PsiElement element) {
    if (element instanceof PsiFunctionalExpression) {
      final PsiType samType = ((PsiFunctionalExpression)element).getFunctionalInterfaceType();
      return getFunctionalInterfaceMethod(samType);
    }
    return null;
  }

  @Nullable
  public static PsiMethod getFunctionalInterfaceMethod(@NotNull PsiClassType.ClassResolveResult result) {
    return getFunctionalInterfaceMethod(result.getElement());
  }

  @Contract("null -> null")
  @Nullable
  public static PsiMethod getFunctionalInterfaceMethod(PsiClass aClass) {
    final MethodSignature methodSignature = getFunction(aClass);
    if (methodSignature != null) {
      return getMethod(aClass, methodSignature);
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
      initialSubst = initialSubst.put(param, initialSubst.substitute(superClassSubstitutor.substitute(param)));
    }
    return initialSubst;
  }

  public static boolean isFunctionalType(PsiType type) {
    if (type instanceof PsiIntersectionType) {
      return extractFunctionalConjunct((PsiIntersectionType)type) != null;
    }
    return isFunctionalClass(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  @Contract("null -> false")
  public static boolean isFunctionalClass(PsiClass aClass) {
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return false;
      return getFunction(aClass) != null;
    }
    return false;
  }

  @Contract("null -> false")
  public static boolean isValidLambdaContext(@Nullable PsiElement context) {
    context = PsiUtil.skipParenthesizedExprUp(context);
    if (isAssignmentOrInvocationContext(context) || context instanceof PsiTypeCastExpression) {
      return true;
    }
    if (context instanceof PsiConditionalExpression) {
      PsiElement parentContext = PsiUtil.skipParenthesizedExprUp(context.getParent());
      if (isAssignmentOrInvocationContext(parentContext)) return true;
      if (parentContext instanceof PsiConditionalExpression) {
        return isValidLambdaContext(parentContext);
      }
    }
    return false;
  }

  @Contract("null -> false")
  private static boolean isAssignmentOrInvocationContext(PsiElement context) {
    return isAssignmentContext(context) || isInvocationContext(context);
  }

  private static boolean isInvocationContext(@Nullable PsiElement context) {
    return context instanceof PsiExpressionList;
  }

  private static boolean isAssignmentContext(PsiElement context) {
    return context instanceof PsiLambdaExpression ||
           context instanceof PsiReturnStatement ||
           context instanceof PsiAssignmentExpression ||
           context instanceof PsiVariable ||
           context instanceof PsiArrayInitializerExpression;
  }

  public static boolean isLambdaFullyInferred(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    final boolean hasParams = expression.getParameterList().getParametersCount() > 0;
    if (hasParams || !PsiType.VOID.equals(getFunctionalInterfaceReturnType(functionalInterfaceType))) {   //todo check that void lambdas without params check
      
      return !dependsOnTypeParams(functionalInterfaceType, functionalInterfaceType, expression);
    }
    return true;
  }

  @Contract("null -> null")
  @Nullable
  public static MethodSignature getFunction(final PsiClass psiClass) {
    if (isPlainInterface(psiClass)) {
      return CachedValuesManager.getCachedValue(psiClass, () -> CachedValueProvider.Result
        .create(calcFunction(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
    }
    return null;
  }

  private static boolean isPlainInterface(PsiClass psiClass) {
    return psiClass != null && psiClass.isInterface() && !psiClass.isAnnotationType();
  }

  @Nullable
  private static MethodSignature calcFunction(@NotNull PsiClass psiClass) {
    if (hasManyOwnAbstractMethods(psiClass) || hasManyInheritedAbstractMethods(psiClass)) return null;

    final List<HierarchicalMethodSignature> functions = findFunctionCandidates(psiClass);
    return functions != null && functions.size() == 1 ? functions.get(0) : null;
  }

  private static boolean hasManyOwnAbstractMethods(@NotNull PsiClass psiClass) {
    int abstractCount = 0;
    for (PsiMethod method : psiClass.getMethods()) {
      if (isDefinitelyAbstractInterfaceMethod(method) && ++abstractCount > 1) {
        return true;
      }
    }
    return false;
  }

  private static boolean isDefinitelyAbstractInterfaceMethod(PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.ABSTRACT) && !isPublicObjectMethod(method.getName());
  }

  private static boolean isPublicObjectMethod(String methodName) {
    return "equals".equals(methodName) || "hashCode".equals(methodName) || "toString".equals(methodName);
  }

  private static boolean hasManyInheritedAbstractMethods(@NotNull PsiClass psiClass) {
    final Set<String> abstractNames = ContainerUtil.newHashSet();
    final Set<String> defaultNames = ContainerUtil.newHashSet();
    InheritanceUtil.processSupers(psiClass, true, psiClass1 -> {
      for (PsiMethod method : psiClass1.getMethods()) {
        if (isDefinitelyAbstractInterfaceMethod(method)) {
          abstractNames.add(method.getName());
        }
        else if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
          defaultNames.add(method.getName());
        }
      }
      return true;
    });
    abstractNames.removeAll(defaultNames);
    return abstractNames.size() > 1;
  }

  private static boolean overridesPublicObjectMethod(HierarchicalMethodSignature psiMethod) {
    final List<HierarchicalMethodSignature> signatures = psiMethod.getSuperSignatures();
    if (signatures.isEmpty()) {
      final PsiMethod method = psiMethod.getMethod();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
          return true;
        }
      }
    }
    for (HierarchicalMethodSignature superMethod : signatures) {
      if (overridesPublicObjectMethod(superMethod)) {
        return true;
      }
    }
    return false;
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

  @NotNull
  private static List<HierarchicalMethodSignature> hasSubSignature(List<HierarchicalMethodSignature> signatures) {
    for (HierarchicalMethodSignature signature : signatures) {
      boolean subSignature = true;
      for (HierarchicalMethodSignature methodSignature : signatures) {
        if (!signature.equals(methodSignature) && !skipMethod(signature, methodSignature)) {
          subSignature = false;
          break;
        }
      }
      if (subSignature) return Collections.singletonList(signature);
    }
    return signatures;
  }

  private static boolean skipMethod(HierarchicalMethodSignature signature,
                                    HierarchicalMethodSignature methodSignature) {
    //not generic
    if (methodSignature.getTypeParameters().length == 0) {
      return false;
    }
    //foreign class
    return signature.getMethod().getContainingClass() != methodSignature.getMethod().getContainingClass();
  }

  @Contract("null -> null")
  @Nullable
  public static List<HierarchicalMethodSignature> findFunctionCandidates(@Nullable final PsiClass psiClass) {
    if (!isPlainInterface(psiClass)) return null;

    final List<HierarchicalMethodSignature> methods = new ArrayList<>();
    final Map<MethodSignature, Set<PsiMethod>> overrideEquivalents = PsiSuperMethodUtil.collectOverrideEquivalents(psiClass);
    final Collection<HierarchicalMethodSignature> visibleSignatures = psiClass.getVisibleSignatures();
    for (HierarchicalMethodSignature signature : visibleSignatures) {
      final PsiMethod psiMethod = signature.getMethod();
      if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) continue;
      final Set<PsiMethod> equivalentMethods = overrideEquivalents.get(signature);
      if (equivalentMethods != null && equivalentMethods.size() > 1) {
        boolean hasNonAbstractOverrideEquivalent = false;
        for (PsiMethod method : equivalentMethods) {
          if (!method.hasModifierProperty(PsiModifier.ABSTRACT) && !MethodSignatureUtil.isSuperMethod(method, psiMethod)) {
            hasNonAbstractOverrideEquivalent = true;
            break;
          }
        }
        if (hasNonAbstractOverrideEquivalent) continue;
      }
      if (!overridesPublicObjectMethod(signature)) {
        methods.add(signature);
      }
    }

    return hasSubSignature(methods);
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
    if (methodSignature instanceof MethodSignatureBackedByPsiMethod) {
      return ((MethodSignatureBackedByPsiMethod)methodSignature).getMethod();
    }

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
                                            PsiType functionalInterfaceType,
                                            PsiElement lambdaExpression,
                                            PsiTypeParameter... param2Check) {
    return depends(type, new TypeParamsChecker(lambdaExpression,
                                               PsiUtil.resolveClassInType(functionalInterfaceType)), param2Check);
  }

  public static boolean depends(PsiType type, TypeParamsChecker visitor, PsiTypeParameter... param2Check) {
    if (!visitor.startedInference()) return false;
    final Boolean accept = type.accept(visitor);
    if (param2Check.length > 0) {
      return visitor.used(param2Check);
    }
    return accept != null && accept.booleanValue();
  }

  @Nullable
  public static PsiType getFunctionalInterfaceType(PsiElement expression, final boolean tryToSubstitute) {
    PsiElement parent = expression.getParent();
    PsiElement element = expression;
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiConditionalExpression) {
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getThenExpression() != element &&
          ((PsiConditionalExpression)parent).getElseExpression() != element) break;
      element = parent;
      parent = parent.getParent();
    }

    final Map<PsiElement, PsiType> map = ourFunctionTypes.get();
    if (map != null) {
      final PsiType type = map.get(expression);
      if (type != null) {
        return type;
      }
    }

    if (parent instanceof PsiArrayInitializerExpression) {
      final PsiType psiType = ((PsiArrayInitializerExpression)parent).getType();
      if (psiType instanceof PsiArrayType) {
        return ((PsiArrayType)psiType).getComponentType();
      }
    } else if (parent instanceof PsiTypeCastExpression) {
      //ensure no capture is performed to target type of cast expression, from 15.16 Cast Expressions:
      //Casts can be used to explicitly "tag" a lambda expression or a method reference expression with a particular target type. 
      //To provide an appropriate degree of flexibility, the target type may be a list of types denoting an intersection type, 
      // provided the intersection induces a functional interface (p9.8).
      final PsiTypeElement castTypeElement = ((PsiTypeCastExpression)parent).getCastType();
      final PsiType castType = castTypeElement != null ? castTypeElement.getType() : null;
      if (castType instanceof PsiIntersectionType) {
        final PsiType conjunct = extractFunctionalConjunct((PsiIntersectionType)castType);
        if (conjunct != null) return conjunct;
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

        PsiElement gParent = expressionList.getParent();

        if (gParent instanceof PsiAnonymousClass) {
          gParent = gParent.getParent();
        }

        if (gParent instanceof PsiCall) {
          final PsiCall contextCall = (PsiCall)gParent;
          final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(contextCall.getArgumentList());
          if (properties != null && properties.isApplicabilityCheck()) { //todo simplification
            final PsiParameter[] parameters = properties.getMethod().getParameterList().getParameters();
            final int finalLambdaIdx = adjustLambdaIdx(lambdaIdx, properties.getMethod(), parameters);
            if (finalLambdaIdx < parameters.length) {
              return properties.getSubstitutor().substitute(getNormalizedType(parameters[finalLambdaIdx]));
            }
          }
          JavaResolveResult resolveResult = properties != null ? properties.getInfo() : PsiDiamondType.getDiamondsAwareResolveResult(contextCall);
          return getSubstitutedType(expression, tryToSubstitute, lambdaIdx, resolveResult);
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      return PsiTypesUtil.getMethodReturnType(parent);
    }
    else if (parent instanceof PsiLambdaExpression) {
      return getFunctionalInterfaceTypeByContainingLambda((PsiLambdaExpression)parent);
    }
    return null;
  }

  @Nullable
  private static PsiType getSubstitutedType(PsiElement expression,
                                            boolean tryToSubstitute,
                                            int lambdaIdx,
                                            final JavaResolveResult resolveResult) {
    final PsiElement resolve = resolveResult.getElement();
    if (resolve instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
      final int finalLambdaIdx = adjustLambdaIdx(lambdaIdx, (PsiMethod)resolve, parameters);
      if (finalLambdaIdx < parameters.length) {
        if (!tryToSubstitute) return getNormalizedType(parameters[finalLambdaIdx]);
        return PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, !MethodCandidateInfo.isOverloadCheck(), () -> {
          final PsiType normalizedType = getNormalizedType(parameters[finalLambdaIdx]);
          if (resolveResult instanceof MethodCandidateInfo && ((MethodCandidateInfo)resolveResult).isRawSubstitution()) {
            return TypeConversionUtil.erasure(normalizedType);
          }
          else {
            return resolveResult.getSubstitutor().substitute(normalizedType);
          }
        });
      }
    }
    return null;
  }

  public static boolean processParentOverloads(PsiFunctionalExpression functionalExpression, final Consumer<PsiType> overloadProcessor) {
    LOG.assertTrue(PsiTypesUtil.getExpectedTypeByParent(functionalExpression) == null);
    PsiElement parent = functionalExpression.getParent();
    PsiElement expr = functionalExpression;
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiConditionalExpression) {
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getThenExpression() != expr &&
          ((PsiConditionalExpression)parent).getElseExpression() != expr) break;
      expr = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final int lambdaIdx = getLambdaIdx(expressionList, functionalExpression);
      if (lambdaIdx > -1) {

        PsiElement gParent = expressionList.getParent();

        if (gParent instanceof PsiAnonymousClass) {
          gParent = gParent.getParent();
        }

        if (gParent instanceof PsiMethodCallExpression) {
          final Set<PsiType> types = new HashSet<>();
          final JavaResolveResult[] results = ((PsiMethodCallExpression)gParent).getMethodExpression().multiResolve(true);
          for (JavaResolveResult result : results) {
            final PsiType functionalExpressionType = getSubstitutedType(functionalExpression, true, lambdaIdx, result);
            if (functionalExpressionType != null && types.add(functionalExpressionType)) {
              overloadProcessor.consume(functionalExpressionType);
            }
          }
          return true;
        }
      }
    }
    return false;
  }
  
  @Nullable
  private static PsiType extractFunctionalConjunct(PsiIntersectionType type) {
    PsiType conjunct = null;
    MethodSignature commonSignature = null;
    for (PsiType psiType : type.getConjuncts()) {
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
      if (aClass instanceof PsiTypeParameter) continue;
      MethodSignature signature = getFunction(aClass);
      if (signature == null) continue;
      if (commonSignature == null) {
        commonSignature = signature;
      }
      else if (!MethodSignatureUtil.areSignaturesEqual(commonSignature, signature)) {
        return null;
      }
      conjunct = psiType;
    }

    return conjunct;
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

  @Contract(value = "null -> false", pure = true)
  public static boolean notInferredType(PsiType typeByExpression) {
    return typeByExpression instanceof PsiMethodReferenceType ||
           typeByExpression instanceof PsiLambdaExpressionType ||
           typeByExpression instanceof PsiLambdaParameterType;
  }

  @NotNull
  public static PsiReturnStatement[] getReturnStatements(PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    return body instanceof PsiCodeBlock ? PsiUtil.findReturnStatements((PsiCodeBlock)body) : PsiReturnStatement.EMPTY_ARRAY;
  }

  public static List<PsiExpression> getReturnExpressions(PsiLambdaExpression lambdaExpression) {
    final PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression) {
      //if (((PsiExpression)body).getType() != PsiType.VOID) return Collections.emptyList();
      return Collections.singletonList((PsiExpression)body);
    }
    final List<PsiExpression> result = new ArrayList<>();
    for (PsiReturnStatement returnStatement : getReturnStatements(lambdaExpression)) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        result.add(returnValue);
      }
    }
    return result;
  }

  //JLS 14.8 Expression Statements
  @Contract("null -> false")
  public static boolean isExpressionStatementExpression(PsiElement body) {
    return body instanceof PsiAssignmentExpression ||
           body instanceof PsiPrefixExpression &&
           (((PsiPrefixExpression)body).getOperationTokenType() == JavaTokenType.PLUSPLUS ||
            ((PsiPrefixExpression)body).getOperationTokenType() == JavaTokenType.MINUSMINUS) ||
           body instanceof PsiPostfixExpression ||
           body instanceof PsiCallExpression ||
           body instanceof PsiReferenceExpression && !body.isPhysical();
  }

  public static PsiExpression extractSingleExpressionFromBody(PsiElement body) {
    PsiExpression expression = null;
    if (body instanceof PsiExpression) {
      expression = (PsiExpression)body;
    }
    else if (body instanceof PsiCodeBlock) {
      final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
      if (statements.length == 1) {
        if (statements[0] instanceof PsiReturnStatement) {
          expression = ((PsiReturnStatement)statements[0]).getReturnValue();
        }
        else if (statements[0] instanceof PsiExpressionStatement) {
          expression = ((PsiExpressionStatement)statements[0]).getExpression();
        }
        else if (statements[0] instanceof PsiBlockStatement) {
          return extractSingleExpressionFromBody(((PsiBlockStatement)statements[0]).getCodeBlock());
        }
      }
    }
    else if (body instanceof PsiBlockStatement) {
      return extractSingleExpressionFromBody(((PsiBlockStatement)body).getCodeBlock());
    }
    else if (body instanceof PsiExpressionStatement) {
      expression = ((PsiExpressionStatement)body).getExpression();
    }
    return expression;
  }

  // http://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.1
  // A lambda expression or a method reference expression is potentially compatible with a type variable 
  // if the type variable is a type parameter of the candidate method.
  public static boolean isPotentiallyCompatibleWithTypeParameter(PsiFunctionalExpression expression,
                                                                 PsiExpressionList argsList,
                                                                 PsiMethod method) {
    if (!Registry.is("JDK8042508.bug.fixed", false)) {
      final PsiCallExpression callExpression = PsiTreeUtil.getParentOfType(argsList, PsiCallExpression.class);
      if (callExpression == null || callExpression.getTypeArguments().length > 0) {
        return false;
      }
    }

    final int lambdaIdx = getLambdaIdx(argsList, expression);
    if (lambdaIdx >= 0) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiParameter lambdaParameter = parameters[Math.min(lambdaIdx, parameters.length - 1)];
      final PsiClass paramClass = PsiUtil.resolveClassInType(lambdaParameter.getType());
      if (paramClass instanceof PsiTypeParameter && ((PsiTypeParameter)paramClass).getOwner() == method) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static Map<PsiElement, PsiType> getFunctionalTypeMap() {
    Map<PsiElement, PsiType> map = ourFunctionTypes.get();
    if (map == null) {
      map = new HashMap<>();
      ourFunctionTypes.set(map);
    }
    return map;
  }

  public static Map<PsiElement, String> checkReturnTypeCompatible(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceReturnType) {
    Map<PsiElement, String> errors = new LinkedHashMap<>();
    if (PsiType.VOID.equals(functionalInterfaceReturnType)) {
      final PsiElement body = lambdaExpression.getBody();
      if (body instanceof PsiCodeBlock) {
        for (PsiExpression expression : getReturnExpressions(lambdaExpression)) {
          errors.put(expression, "Unexpected return value");
        }
      }
      else if (body instanceof PsiExpression) {
        final PsiType type = ((PsiExpression)body).getType();
        try {
          if (!PsiUtil.isStatement(JavaPsiFacade.getElementFactory(body.getProject()).createStatementFromText(body.getText(), body))) {
            if (PsiType.VOID.equals(type)) {
              errors.put(body, "Lambda body must be a statement expression");
            }
            else {
              errors.put(body, "Bad return type in lambda expression: " + (type == PsiType.NULL || type == null ? "<null>" : type.getPresentableText()) + " cannot be converted to void");
            }
          }
        }
        catch (IncorrectOperationException ignore) {
        }
      }
    }
    else if (functionalInterfaceReturnType != null) {
      final List<PsiExpression> returnExpressions = getReturnExpressions(lambdaExpression);
      for (final PsiExpression expression : returnExpressions) {
        final PsiType expressionType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, true, () -> expression.getType());
        if (expressionType != null && !functionalInterfaceReturnType.isAssignableFrom(expressionType)) {
          errors.put(expression, "Bad return type in lambda expression: " + expressionType.getPresentableText() + " cannot be converted to " + functionalInterfaceReturnType.getPresentableText());
        }
      }
      final PsiReturnStatement[] returnStatements = getReturnStatements(lambdaExpression);
      if (returnStatements.length > returnExpressions.size()) {
        for (PsiReturnStatement statement : returnStatements) {
          final PsiExpression value = statement.getReturnValue();
          if (value == null) {
            errors.put(statement, "Missing return value");
          }
        }
      }
      else if (returnExpressions.isEmpty() && !lambdaExpression.isVoidCompatible()) {
        errors.put(lambdaExpression, "Missing return value");
      }
    }
    return errors.isEmpty() ? null : errors;
  }

  @Nullable
  public static PsiType getLambdaParameterFromType(PsiType functionalInterfaceType, int parameterIndex) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod method = getFunctionalInterfaceMethod(functionalInterfaceType);
    if (method != null) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameterIndex < parameters.length) {
        return getSubstitutor(method, resolveResult).substitute(parameters[parameterIndex].getType());
      }
    }
    return null;
  }

  public static boolean isLambdaParameterCheck() {
    return !ourParameterGuard.currentStack().isEmpty();
  }

  @Nullable
  public static PsiCall treeWalkUp(PsiElement context) {
    PsiCall top = null;
    PsiElement parent = PsiTreeUtil.getParentOfType(context,
                                                    PsiExpressionList.class,
                                                    PsiLambdaExpression.class,
                                                    PsiConditionalExpression.class,
                                                    PsiCodeBlock.class,
                                                    PsiCall.class);
    while (true) {
      if (parent instanceof PsiCall) {
        break;
      }

      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
      if (parent instanceof PsiCodeBlock) {
        if (lambdaExpression == null) {
          break;
        }
        else {
          boolean inReturnExpressions = false;
          for (PsiExpression expression : getReturnExpressions(lambdaExpression)) {
            inReturnExpressions |= PsiTreeUtil.isAncestor(expression, context, false);
          }

          if (!inReturnExpressions) {
            break;
          }

          if (getFunctionalTypeMap().containsKey(lambdaExpression)) {
            break;
          }
        }
      }

      if (parent instanceof PsiConditionalExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)parent)) {
        break;
      }

      if (parent instanceof PsiLambdaExpression && getFunctionalTypeMap().containsKey(parent)) {
        break;
      }

      final PsiCall psiCall = PsiTreeUtil.getParentOfType(parent, PsiCall.class, false, PsiMember.class, PsiVariable.class);
      if (psiCall == null) {
        break;
      }
      final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(psiCall.getArgumentList());
      if (properties != null) {
        if (properties.isApplicabilityCheck() ||
            lambdaExpression != null && lambdaExpression.hasFormalParameterTypes()) {
          break;
        }
      }

      top = psiCall;
      if (top instanceof PsiExpression && PsiPolyExpressionUtil.isPolyExpression((PsiExpression)top)) {
        parent = PsiTreeUtil.getParentOfType(parent.getParent(), PsiExpressionList.class, PsiLambdaExpression.class, PsiCodeBlock.class);
      }
      else {
        break;
      }
    }

    if (top == null) {
      return null;
    }

    final PsiExpressionList argumentList = top.getArgumentList();
    if (argumentList == null) {
      return null;
    }

    LOG.assertTrue(MethodCandidateInfo.getCurrentMethod(argumentList) == null);
    return top;
  }

  public static PsiCall copyTopLevelCall(@NotNull PsiCall call) {
    PsiCall copyCall = (PsiCall)call.copy();
    if (call instanceof PsiEnumConstant) {
      PsiClass containingClass = ((PsiEnumConstant)call).getContainingClass();
      if (containingClass == null) {
        return null;
      }
      String enumName = containingClass.getName();
      if (enumName == null) {
        return null;
      }
      PsiMethod resolveMethod = call.resolveMethod();
      if (resolveMethod == null) {
        return null;
      }
      PsiClass anEnum = JavaPsiFacade.getElementFactory(call.getProject()).createEnum(enumName);
      anEnum.add(resolveMethod);
      return  (PsiCall)anEnum.add(call);
    }
    return copyCall;
  }

  public static <T> T performWithSubstitutedParameterBounds(final PsiTypeParameter[] typeParameters,
                                                            final PsiSubstitutor substitutor,
                                                            final Producer<T> producer) {
    try {
      for (PsiTypeParameter parameter : typeParameters) {
        final PsiClassType[] types = parameter.getExtendsListTypes();
        if (types.length > 0) {
          final List<PsiType> conjuncts = ContainerUtil.map(types, type -> substitutor.substitute(type));
          //don't glb to avoid flattening = Object&Interface would be preserved
          //otherwise methods with different signatures could get same erasure
          final PsiType upperBound = PsiIntersectionType.createIntersection(false, conjuncts.toArray(new PsiType[conjuncts.size()]));
          getFunctionalTypeMap().put(parameter, upperBound);
        }
      }
      return producer.produce();
    }
    finally {
      for (PsiTypeParameter parameter : typeParameters) {
        getFunctionalTypeMap().remove(parameter);
      }
    }
  }

  public static <T> T performWithLambdaTargetType(PsiLambdaExpression lambdaExpression, PsiType targetType, Producer<T> producer) {
    try {
      getFunctionalTypeMap().put(lambdaExpression, targetType);
      return producer.produce();
    }
    finally {
      getFunctionalTypeMap().remove(lambdaExpression);
    }
  }

  /**
   * Generate lambda text for single argument expression lambda
   *
   * @param variable lambda sole argument
   * @param expression lambda body (expression)
   * @return lambda text
   */
  public static String createLambda(@NotNull PsiVariable variable, @NotNull PsiExpression expression) {
    return variable.getName() + " -> " + expression.getText();
  }

  /**
   * Returns true if lambda has single parameter and its return value is the same as parameter.
   *
   * <p>
   * The lambdas like this are considered identity lambda: {@code x -> x}, {@code x -> {return x;}}
   * {@code (String x) -> (x)}, etc.</p>
   *
   * <p>
   * This method does not check the lambda type, also it does not check whether auto-(un)boxing occurs,
   * so a lambda like {@code ((Predicate<Boolean>)b -> b)} is also identity lambda even though it performs
   * auto-unboxing.
   * </p>
   *
   * @param lambda a lambda to check
   * @return true if the supplied lambda is an identity lambda
   */
  public static boolean isIdentityLambda(PsiLambdaExpression lambda) {
    PsiParameterList parameters = lambda.getParameterList();
    if(parameters.getParametersCount() != 1) return false;
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(extractSingleExpressionFromBody(lambda.getBody()));
    return expression instanceof PsiReferenceExpression &&
           ((PsiReferenceExpression)expression).isReferenceTo(parameters.getParameters()[0]);
  }

  public static class TypeParamsChecker extends PsiTypeVisitor<Boolean> {
    private PsiMethod myMethod;
    private final PsiClass myClass;
    public final Set<PsiTypeParameter> myUsedTypeParams = new HashSet<>();

    public TypeParamsChecker(PsiElement expression, PsiClass aClass) {
      myClass = aClass;
      PsiElement parent = expression != null ? expression.getParent() : null;
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiExpressionList) {
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiCall) {
          final MethodCandidateInfo.CurrentCandidateProperties pair = MethodCandidateInfo.getCurrentMethod(parent);
          myMethod = pair != null ? pair.getMethod() : null;
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
      return true;
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
      if (owner == myMethod || owner == myClass) {
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