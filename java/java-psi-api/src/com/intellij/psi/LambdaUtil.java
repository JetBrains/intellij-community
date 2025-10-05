// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utilities around java 8 functional expressions.
 */
public final class LambdaUtil {
  private static final Logger LOG = Logger.getInstance(LambdaUtil.class);

  /**
   * @return substituted return type of expression's SAM method
   */
  public static @Nullable PsiType getFunctionalInterfaceReturnType(@NotNull PsiFunctionalExpression expr) {
    return getFunctionalInterfaceReturnType(expr.getFunctionalInterfaceType());
  }

   /**
   * @return substituted return type of method which corresponds to the {@code functionalInterfaceType} SAM,
    *        null when {@code functionalInterfaceType} doesn't correspond to functional interface type
   */
  public static @Nullable PsiType getFunctionalInterfaceReturnType(@Nullable PsiType functionalInterfaceType) {
    PsiType functionalType = normalizeFunctionalType(functionalInterfaceType);
    if (!(functionalType instanceof PsiClassType)) return null;
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalType);
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

  /**
   * @return abstract method of SAM interface which corresponds to {@code functionalInterfaceType}, null otherwise
   */
  @Contract("null -> null")
  public static @Nullable PsiMethod getFunctionalInterfaceMethod(@Nullable PsiType functionalInterfaceType) {
    return getFunctionalInterfaceMethod(PsiUtil.resolveClassInClassTypeOnly(normalizeFunctionalType(functionalInterfaceType)));
  }

  public static PsiMethod getFunctionalInterfaceMethod(@Nullable PsiElement element) {
    return element instanceof PsiFunctionalExpression ? getFunctionalInterfaceMethod(((PsiFunctionalExpression)element).getFunctionalInterfaceType()) : null;
  }

  public static @Nullable PsiMethod getFunctionalInterfaceMethod(@NotNull PsiClassType.ClassResolveResult result) {
    return getFunctionalInterfaceMethod(result.getElement());
  }

  @Contract("null -> null")
  public static @Nullable PsiMethod getFunctionalInterfaceMethod(PsiClass aClass) {
    final MethodSignature methodSignature = getFunction(aClass);
    return methodSignature != null ? getMethod(aClass, methodSignature) : null;
  }

  /**
   * Extract functional interface from intersection
   */
  public static @Nullable PsiType normalizeFunctionalType(@Nullable PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType) {
      PsiType functionalConjunct = extractFunctionalConjunct((PsiIntersectionType)functionalInterfaceType);
      if (functionalConjunct != null) {
        functionalInterfaceType = functionalConjunct;
      }
    }
    return functionalInterfaceType;
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
    return isFunctionalClass(PsiUtil.resolveClassInClassTypeOnly(normalizeFunctionalType(type)));
  }

  @Contract("null -> false")
  public static boolean isFunctionalClass(PsiClass aClass) {
    return getFunction(aClass) != null;
  }

  @Contract("null -> false")
  public static boolean isValidLambdaContext(@Nullable PsiElement context) {
    context = PsiUtil.skipParenthesizedExprUp(context);
    if (context instanceof PsiTypeCastExpression) {
      return true;
    }

    while (true) {
      context = PsiUtil.skipParenthesizedExprUp(context);
      if (isAssignmentOrInvocationContext(context)) {
        return true;
      }
      if (context instanceof PsiConditionalExpression) {
        context = context.getParent();
        continue;
      }
      if (context instanceof PsiYieldStatement) {
        PsiSwitchExpression switchExpression = ((PsiYieldStatement)context).findEnclosingExpression();
        if (switchExpression != null) {
          context = switchExpression.getParent();
          continue;
        }
      }
      if (context instanceof PsiExpressionStatement) {
        PsiElement parent = context.getParent();
        if (parent instanceof PsiSwitchLabeledRuleStatement) {
          PsiSwitchBlock switchBlock = ((PsiSwitchLabeledRuleStatement)parent).getEnclosingSwitchBlock();
          if (switchBlock != null) {
            context = switchBlock.getParent();
            continue;
          }
        }
      }
      return false;
    }
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
           context instanceof PsiVariable && !withInferredType((PsiVariable)context) ||
           context instanceof PsiArrayInitializerExpression;
  }

  private static boolean withInferredType(PsiVariable variable) {
    PsiTypeElement typeElement = variable.getTypeElement();
    return typeElement != null && typeElement.isInferredType();
  }

  @Contract("null -> null")
  public static @Nullable MethodSignature getFunction(final @Nullable PsiClass psiClass) {
    if (isPlainInterface(psiClass)) {
      return CachedValuesManager.getProjectPsiDependentCache(psiClass, LambdaUtil::calcFunction);
    }
    return null;
  }

  private static boolean isPlainInterface(@Nullable PsiClass psiClass) {
    return psiClass != null && psiClass.isInterface() && !psiClass.isAnnotationType();
  }

  private static @Nullable MethodSignature calcFunction(@NotNull PsiClass psiClass) {
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
    final Set<String> abstractNames = new HashSet<>();
    final Set<String> defaultNames = new HashSet<>();
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

  private static @NotNull List<HierarchicalMethodSignature> hasSubSignature(List<HierarchicalMethodSignature> signatures) {
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
  public static @Nullable List<HierarchicalMethodSignature> findFunctionCandidates(@Nullable PsiClass psiClass) {
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


  private static @Nullable PsiType getReturnType(PsiClass psiClass, MethodSignature methodSignature) {
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

  private static @Nullable PsiMethod getMethod(PsiClass psiClass, MethodSignature methodSignature) {
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

  public static @Nullable PsiType getFunctionalInterfaceType(PsiElement expression, boolean tryToSubstitute) {
    PsiElement parent = expression.getParent();
    PsiElement element = expression;
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiConditionalExpression) {
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() == element) {
        return PsiTypes.booleanType();
      }
      element = parent;
      parent = parent.getParent();
    }

    PsiType type = ThreadLocalTypes.getElementType(expression);
    if (type == null) type = ThreadLocalTypes.getElementType(element);
    if (type != null) {
      return type;
    }

    if (parent instanceof PsiArrayInitializerExpression) {
      final PsiType psiType = ((PsiArrayInitializerExpression)parent).getType();
      if (psiType instanceof PsiArrayType) {
        return ((PsiArrayType)psiType).getComponentType();
      }
    }
    else if (parent instanceof PsiTypeCastExpression) {
      // Ensure no capture is performed to target type of cast expression, from 15.16 "Cast Expressions":
      // Casts can be used to explicitly "tag" a lambda expression or a method reference expression with a particular target type.
      // To provide an appropriate degree of flexibility, the target type may be a list of types denoting an intersection type,
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
      PsiVariable variable = (PsiVariable)parent;
      PsiTypeElement typeElement = variable.getTypeElement();
      return typeElement != null && typeElement.isInferredType() ? null : variable.getType();
    }
    else if (parent instanceof PsiAssignmentExpression && expression instanceof PsiExpression && !PsiUtil.isOnAssignmentLeftHand((PsiExpression)expression)) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      return lExpression.getType();
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final int lambdaIdx = getLambdaIdx(expressionList, expression);
      if (lambdaIdx >= 0) {
        PsiElement gParent = expressionList.getParent();

        if (gParent instanceof PsiAnonymousClass) {
          gParent = gParent.getParent();
        }

        if (gParent instanceof PsiCall) {
          final PsiCall contextCall = (PsiCall)gParent;
          JavaResolveResult resolveResult = PsiDiamondType.getDiamondsAwareResolveResult(contextCall);
          PsiElement resultElement = resolveResult.getElement();
          if (resultElement == null) {
            return tryGetFunctionalTypeFromMultiResolve(expression, tryToSubstitute, contextCall, lambdaIdx);
          }
          LOG.assertTrue(!(MethodCandidateInfo.isOverloadCheck(contextCall.getArgumentList()) &&
                           resultElement instanceof PsiMethod &&
                           ((PsiMethod)resultElement).hasTypeParameters() &&
                           contextCall instanceof PsiCallExpression && ((PsiCallExpression)contextCall).getTypeArguments().length == 0));
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
    PsiSwitchExpression switchExpression = PsiTreeUtil.getParentOfType(element, PsiSwitchExpression.class);
    if (switchExpression != null && PsiUtil.getSwitchResultExpressions(switchExpression).contains(element)) {
      return getFunctionalInterfaceType(switchExpression, tryToSubstitute);
    }
    return null;
  }

  /**
   * It's possible that we have several overloads, but all of them resolve
   * the same functional parameter to the same functional interface type.
   * In this case, we can safely return that type.
   * <p>
   * Example: {@code Collectors.toMap(t -> {})}
   * <p>
   * This call is not resolved, as there are several overloads of {@code Collectors.toMap()},
   * but all of them have a key-mapper lambda as the first parameter, so we can determine the functional interface type.
   */
  private static @Nullable PsiType tryGetFunctionalTypeFromMultiResolve(PsiElement expression,
                                                                        boolean tryToSubstitute,
                                                                        PsiCall contextCall,
                                                                        int lambdaIdx) {
    JavaResolveResult[] results = getCallCandidates(contextCall);
    PsiType firstType = null;
    for (JavaResolveResult result : results) {
      PsiType substitutedType = getSubstitutedType(expression, tryToSubstitute, lambdaIdx, result);
      if (substitutedType == null) {
        return null;
      }
      if (firstType == null) {
        firstType = substitutedType;
      }
      else if (!substitutedType.equals(firstType)) {
        return null;
      }
    }
    return firstType;
  }

  private static @Nullable PsiType getSubstitutedType(PsiElement expression,
                                                      boolean tryToSubstitute,
                                                      int lambdaIdx,
                                                      JavaResolveResult resolveResult) {
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

  public static boolean processParentOverloads(PsiFunctionalExpression functionalExpression, final Consumer<? super PsiType> overloadProcessor) {
    if (PsiTypesUtil.getExpectedTypeByParent(functionalExpression) != null) return false;
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

        if (gParent instanceof PsiCall) {
          JavaResolveResult[] results = getCallCandidates((PsiCall)gParent);
          final Set<PsiType> types = new HashSet<>();
          for (JavaResolveResult result : results) {
            Computable<PsiType> computeType = () -> getSubstitutedType(functionalExpression, true, lambdaIdx, result);
            final PsiType functionalExpressionType = results.length == 1
                                                     ? computeType.compute() 
                                                     : MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(functionalExpression, false, computeType);
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

  private static JavaResolveResult @NotNull [] getCallCandidates(@NotNull PsiCall contextCall) {
    if (contextCall instanceof PsiNewExpression) {
      PsiDiamondType diamondType = PsiDiamondType.getDiamondType((PsiNewExpression)contextCall);
      if (diamondType != null) {
        JavaResolveResult[] results = diamondType.getStaticFactories();
        if (results.length > 0) {
          List<JavaResolveResult> substituted = ContainerUtil.filter(results, result -> {
            return !ContainerUtil.exists(result.getSubstitutor().getSubstitutionMap().entrySet(),
                                        e -> PsiUtil.resolveClassInClassTypeOnly(e.getValue()) == e.getKey());
          });
          if (!substituted.isEmpty()) {
            // Prefer only candidates where inference was successful
            return substituted.toArray(JavaResolveResult.EMPTY_ARRAY);
          }
        }
        return results;
      }
    }
    return contextCall.multiResolve(true);
  }


  private static @Nullable PsiType extractFunctionalConjunct(PsiIntersectionType type) {
    PsiType conjunct = null;
    PsiClass commonClass = null;
    MethodSignature commonSignature = null;
    for (PsiType psiType : type.getConjuncts()) {
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
      if (aClass instanceof PsiTypeParameter) continue;
      MethodSignature signature = getFunction(aClass);
      if (signature == null) continue;
      if (commonSignature == null) {
        commonSignature = signature;
        commonClass = aClass;
      }
      else if (!MethodSignatureUtil.areSignaturesEqual(commonSignature, signature)) {
        return null;
      }
      conjunct = psiType;
    }
    if (conjunct == null) {
      return null;
    }
    PsiMethod method = getMethod(commonClass, commonSignature);
    if (method == null) {
      return null;
    }
    for (PsiType typeConjunct : type.getConjuncts()) {
      if (typeConjunct == conjunct) {
        continue;
      }
      TargetMethodContainer container = getTargetMethod(typeConjunct, commonSignature, conjunct);
      if (container == null) {
        continue;
      }
      PsiMethod inheritor = container.inheritor;
      PsiMethod target = container.targetMethod;
      if (!inheritor.hasModifier(JvmModifier.ABSTRACT) &&
          isLambdaSubsignature(method, conjunct, target, typeConjunct)) {
        return null;
      }
    }
    return conjunct;
  }


  public static class TargetMethodContainer{
    public final @NotNull PsiMethod targetMethod;
    public final @NotNull PsiMethod inheritor;

    private TargetMethodContainer(@NotNull PsiMethod method, @NotNull PsiMethod inheritor) {
      this.targetMethod = method;
      this.inheritor = inheritor;
    }
  }

  /**
   * Returns the target method based on the given type, method signature, and base type.
   *
   * @param type         the type to find the target method in
   * @param signature    the method signature of the target method
   * @param baseType     the base type to resolve the class from
   * @return the target method if found, or null if not found
   */
  public static @Nullable TargetMethodContainer getTargetMethod(@NotNull PsiType type, @NotNull MethodSignature signature, @NotNull PsiType baseType) {
    PsiClass baseClass = PsiUtil.resolveClassInClassTypeOnly(baseType);
    if (baseClass == null) {
      return null;
    }
    PsiMethod method = getMethod(baseClass, signature);
    if (method == null) {
      return null;
    }
    MethodSignature emptyCommonSignature = method.getSignature(PsiSubstitutor.EMPTY);
    PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(type);
    PsiClass aClass = classResolveResult.getElement();
    if (aClass == null) {
      return null;
    }
    PsiMethod methodBySignature = MethodSignatureUtil.findMethodBySignature(aClass, emptyCommonSignature, true);
    if (methodBySignature == null) {
      return null;
    }
    PsiMethod inheritor = MethodSignatureUtil.findMethodBySuperMethod(aClass, methodBySignature, true);
    if (inheritor == null) {
      return null;
    }
    return new TargetMethodContainer(methodBySignature, inheritor);
  }

  public static boolean isLambdaSubsignature(@NotNull PsiMethod method1,
                                             @NotNull PsiType type1,
                                             @NotNull PsiMethod method2,
                                             @NotNull PsiType type2) {
    MethodSignature signature1 = method1.getSignature(PsiSubstitutor.EMPTY);
    MethodSignature signature2 = method2.getSignature(PsiSubstitutor.EMPTY);
    if (!MethodSignatureUtil.isSubsignature(signature1, signature2) &&
        !MethodSignatureUtil.isSubsignature(signature2, signature1)) {
      return false;
    }
    signature1 = getSignatureWithSubstitutors(method1, type1);
    signature2 = getSignatureWithSubstitutors(method2, type2);
    if (!MethodSignatureUtil.isSubsignature(signature1, signature2) &&
        !MethodSignatureUtil.isSubsignature(signature2, signature1)) {
      return false;
    }

    return true;
  }

  private static @NotNull MethodSignature getSignatureWithSubstitutors(@NotNull PsiMethod method, @NotNull PsiType type) {
    PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass aClass = classResolveResult.getElement();
    if (aClass == null) return method.getSignature(PsiSubstitutor.EMPTY);
    PsiClass methodContainingClass = method.getContainingClass();
    if (methodContainingClass == null) return method.getSignature(PsiSubstitutor.EMPTY);
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(methodContainingClass, aClass, classResolveResult.getSubstitutor());
    if (substitutor == null) {
      substitutor = PsiSubstitutor.EMPTY;
    }
    return method.getSignature(substitutor);
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

  public static PsiReturnStatement @NotNull [] getReturnStatements(PsiLambdaExpression lambdaExpression) {
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
           PsiUtil.isIncrementDecrementOperation(body) ||
           body instanceof PsiMethodCallExpression || //method invocation
           body instanceof PsiNewExpression && !((PsiNewExpression)body).isArrayCreation() || //class instance creation
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

  /**
   * If called to check applicability or anything which may be required to determine type, outer caching prevention is required
   * For example, {@link com.intellij.psi.impl.source.tree.java.PsiLambdaExpressionImpl#isAcceptable(PsiType, PsiMethod)}:
   * {@code return LambdaUtil.performWithTargetType(this, leftType, () ->
   *                                                LambdaUtil.checkReturnTypeCompatible(this, substitutor.substitute(methodReturnType)) == null);}
   *                                                
   * otherwise calls to {@code expr#getType()} inside the method may lead to infinite recursion
   */
  public static Map<PsiElement, @Nls String> checkReturnTypeCompatible(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceReturnType) {
    Map<PsiElement, @Nls String> errors = new LinkedHashMap<>();
    if (PsiTypes.voidType().equals(functionalInterfaceReturnType)) {
      final PsiElement body = lambdaExpression.getBody();
      if (body instanceof PsiCodeBlock) {
        for (PsiExpression expression : getReturnExpressions(lambdaExpression)) {
          errors.put(expression, JavaPsiBundle.message("unexpected.return.value"));
        }
      }
      else if (body instanceof PsiExpression) {
        try {
          if (!PsiUtil.isStatement(JavaPsiFacade.getElementFactory(body.getProject()).createStatementFromText(body.getText(), body))) {
            final PsiType type = ((PsiExpression)body).getType();
            if (PsiTypes.voidType().equals(type)) {
              errors.put(body, JavaPsiBundle.message("lambda.body.must.be.a.statement.expression"));
            }
            else {
              errors.put(body, JavaPsiBundle.message("bad.return.type.in.lambda.expression1",
                                                     (type == PsiTypes.nullType() || type == null ? "<null>" : type.getPresentableText())));
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
        final PsiType expressionType = expression.getType();
        if (expressionType != null && !functionalInterfaceReturnType.isAssignableFrom(expressionType)) {
          errors.put(expression, JavaPsiBundle.message("bad.return.type.in.lambda.expression", expressionType.getPresentableText(),
                                                       functionalInterfaceReturnType.getPresentableText()));
        }
      }
      final PsiReturnStatement[] returnStatements = getReturnStatements(lambdaExpression);
      if (returnStatements.length > returnExpressions.size()) {
        for (PsiReturnStatement statement : returnStatements) {
          final PsiExpression value = statement.getReturnValue();
          if (value == null) {
            errors.put(statement, JavaPsiBundle.message("missing.return.value.lambda"));
          }
        }
      }
      else if (returnExpressions.isEmpty() && !lambdaExpression.isVoidCompatible()) {
        errors.put(lambdaExpression, JavaPsiBundle.message("missing.return.value.lambda"));
      }
    }
    return errors.isEmpty() ? null : errors;
  }

  public static @Nullable PsiType getLambdaParameterFromType(PsiType functionalInterfaceType, int parameterIndex) {
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

  public static @Nullable PsiCall treeWalkUp(PsiElement context) {
    PsiCall top = null;
    PsiElement parent = PsiTreeUtil.getParentOfType(context,
                                                    PsiExpressionList.class,
                                                    PsiLambdaExpression.class,
                                                    PsiConditionalExpression.class,
                                                    PsiSwitchExpression.class,
                                                    PsiAssignmentExpression.class,
                                                    PsiCodeBlock.class,
                                                    PsiCall.class);
    while (true) {
      if (parent instanceof PsiCall || parent instanceof PsiAssignmentExpression) {
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

          if (ThreadLocalTypes.hasBindingFor(lambdaExpression)) {
            break;
          }
        }
      }

      if ((parent instanceof PsiConditionalExpression || parent instanceof PsiSwitchExpression) && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)parent)) {
        break;
      }

      if (parent instanceof PsiLambdaExpression && ThreadLocalTypes.hasBindingFor(parent)) {
        break;
      }

      final PsiCall psiCall = PsiTreeUtil.getParentOfType(parent, PsiCall.class, false, PsiVariable.class, PsiMethod.class,
                                                          PsiAssignmentExpression.class, PsiTypeCastExpression.class);
      if (psiCall == null || !PsiTreeUtil.isAncestor(psiCall.getArgumentList(), parent, false)) {
        break;
      }
      if (MethodCandidateInfo.isOverloadCheck(psiCall.getArgumentList()) ||
          lambdaExpression != null && ThreadLocalTypes.hasBindingFor(lambdaExpression)) {
        break;
      }

      top = psiCall;
      if (top instanceof PsiExpression && PsiPolyExpressionUtil.isPolyExpression((PsiExpression)top)) {
        parent = PsiTreeUtil.getParentOfType(parent.getParent(), PsiExpressionList.class, PsiLambdaExpression.class, PsiAssignmentExpression.class, PsiCodeBlock.class);
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

    LOG.assertTrue(!MethodCandidateInfo.isOverloadCheck(argumentList));
    return top;
  }

  public static PsiCall copyTopLevelCall(@NotNull PsiCall call) {
    if (call instanceof PsiEnumConstant) {
      PsiFile contextFile = (PsiFile)call.getContainingFile().copy();
      return PsiTreeUtil.findSameElementInCopy(call, contextFile);
    }
    PsiElement expressionForType = call;
    while (true) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(expressionForType.getParent());
      if (!(parent instanceof PsiConditionalExpression) ||
          PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expressionForType, false)) {
        break;
      }
      expressionForType = parent;
    }
    PsiType type = PsiTypesUtil.getExpectedTypeByParent(expressionForType);
    if (PsiTypesUtil.isDenotableType(type, call)) {
      return (PsiCall)copyWithExpectedType(call, type);
    }
    return (PsiCall)call.copy();
  }

  public static <T> T performWithSubstitutedParameterBounds(final PsiTypeParameter[] typeParameters,
                                                            final PsiSubstitutor substitutor,
                                                            final Supplier<? extends T> producer) {
    return ThreadLocalTypes.performWithTypes(map -> {
      for (PsiTypeParameter parameter : typeParameters) {
        final PsiClassType[] types = parameter.getExtendsListTypes();
        if (types.length > 0) {
          final List<PsiType> conjuncts = ContainerUtil.map(types, substitutor::substitute);
          //don't glb to avoid flattening = Object&Interface would be preserved
          //otherwise methods with different signatures could get same erasure
          final PsiType upperBound = PsiIntersectionType.createIntersection(false, conjuncts.toArray(PsiType.EMPTY_ARRAY));
          map.forceType(parameter, upperBound);
        }
      }
      return producer.get();
    });
  }

  public static <T> T performWithTargetType(@NotNull PsiElement element, @NotNull PsiType targetType, @NotNull Supplier<? extends T> producer) {
    return ThreadLocalTypes.performWithTypes(types -> {
      types.forceType(element, targetType);
      return producer.get();
    });
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
    if (parameters.getParametersCount() != 1) return false;
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(extractSingleExpressionFromBody(lambda.getBody()));
    return expression instanceof PsiReferenceExpression &&
           ((PsiReferenceExpression)expression).isReferenceTo(parameters.getParameters()[0]);
  }

  private static boolean isSafeLambdaReplacement(@NotNull PsiLambdaExpression lambda,
                                                 @NotNull Function<? super PsiLambdaExpression, ? extends PsiExpression> replacer) {
    PsiElement body = lambda.getBody();
    if (body == null) return false;
    final PsiCall call = treeWalkUp(body);
    if (call != null) {
      Object marker = new Object();
      PsiTreeUtil.mark(lambda, marker);
      PsiType origType = call instanceof PsiExpression ? ((PsiExpression)call).getType() : null;
      PsiCall copyCall = copyTopLevelCall(call);
      if (copyCall == null) return false;
      PsiLambdaExpression lambdaCopy = ObjectUtils.tryCast(PsiTreeUtil.releaseMark(copyCall, marker), PsiLambdaExpression.class);
      if (lambdaCopy == null) return false;
      PsiExpression function = replacer.apply(lambdaCopy);
      if (function == null) return false;
      PsiElement copyCur = function, cur = lambda;
      while(cur != call) {
        cur = cur.getParent();
        copyCur = copyCur.getParent();
        if (cur instanceof PsiCall && copyCur instanceof PsiCall) {
          JavaResolveResult result = ((PsiCall)cur).resolveMethodGenerics();
          JavaResolveResult resultCopy = ((PsiCall)copyCur).resolveMethodGenerics();
          if (!equalResolveResult(result, resultCopy)) return false;
        }
      }
      if (function instanceof PsiFunctionalExpression) {
        PsiType functionalType = ((PsiFunctionalExpression)function).getFunctionalInterfaceType();
        PsiType lambdaFunctionalType = lambda.getFunctionalInterfaceType();
        boolean sameType = functionalType == null ? lambdaFunctionalType == null :
                           lambdaFunctionalType != null &&
                           functionalType.getCanonicalText().equals(lambdaFunctionalType.getCanonicalText());                   
        if (!sameType) return false;
      }
      if (origType instanceof PsiClassType && !((PsiClassType)origType).isRaw() &&
          //when lambda has no formal parameter types, it's ignored during applicability check
          //so unchecked warnings inside lambda's body won't lead to erasure of the type of the containing call
          //but after replacement of lambda with the equivalent method call, unchecked warning won't be ignored anymore
          //and the type of the call would be erased => red code may appear
          !lambda.hasFormalParameterTypes()) {
        PsiExpression expressionFromBody = extractSingleExpressionFromBody(body);
        if (expressionFromBody instanceof PsiMethodCallExpression &&
            PsiTypesUtil.isUncheckedCall(((PsiMethodCallExpression)expressionFromBody).resolveMethodGenerics())) {
          return false;
        }
      }
    }
    else if (PsiPolyExpressionUtil.isInAssignmentOrInvocationContext(lambda)) {
      PsiType origType = lambda.getFunctionalInterfaceType();
      if (origType != null) {
        PsiLambdaExpression lambdaCopy = (PsiLambdaExpression)copyWithExpectedType(lambda, origType);
        PsiExpression replacement = replacer.apply(lambdaCopy);
        PsiType type = replacement.getType();
        return type != null && origType.isAssignableFrom(type);
      }
    }
    return true;
  }

  private static boolean equalResolveResult(JavaResolveResult r1, JavaResolveResult r2) {
    PsiElement target1 = r1.getElement();
    PsiElement target2 = r2.getElement();
    boolean targetMatch = target1 == null ? target2 == null : target1.getManager().areElementsEquivalent(target2, target1); 
    if (!targetMatch) return false;
    boolean applicable1 = !(r1 instanceof MethodCandidateInfo) || ((MethodCandidateInfo)r1).isApplicable();
    boolean applicable2 = !(r2 instanceof MethodCandidateInfo) || ((MethodCandidateInfo)r2).isApplicable();
    if (applicable1 != applicable2) return false;
    String message1 = r1 instanceof MethodCandidateInfo ? ((MethodCandidateInfo)r1).getInferenceErrorMessage() : null;
    String message2 = r2 instanceof MethodCandidateInfo ? ((MethodCandidateInfo)r2).getInferenceErrorMessage() : null;
    return Objects.equals(message1, message2);
  }

  /**
   * Returns false if after suggested replacement of lambda body, containing method call would resolve to something else
   * or its return type will change.
   *
   * @param lambda              a lambda whose body is going to be replaced
   * @param newFunctionSupplier replacement for lambda to check,
   *                            lazy computed for lambdas in invocation context only.
   *                            Replacement evaluated to {@code null} is treated as invalid overload
   */
  public static boolean isSafeLambdaReplacement(@NotNull PsiLambdaExpression lambda, @NotNull Supplier<? extends PsiExpression> newFunctionSupplier) {
    return isSafeLambdaReplacement(lambda, l -> {
      PsiExpression replacement = newFunctionSupplier.get();
      return replacement == null ? null : (PsiExpression)l.replace(replacement);
    });
  }

  /**
   * Returns false if after suggested replacement of lambda body, containing method call would resolve to something else
   * or its return type will change.
   *
   * @param lambda          a lambda whose body is going to be replaced
   * @param replacementText a text of new expression to replace lambda
   */
  public static boolean isSafeLambdaReplacement(@NotNull PsiLambdaExpression lambda, @NotNull String replacementText) {
    return isSafeLambdaReplacement(lambda, () -> JavaPsiFacade.getElementFactory(lambda.getProject())
      .createExpressionFromText(replacementText, lambda.getParent()));
  }

  /**
   * Returns false if after suggested replacement of lambda body, containing method call would resolve to something else
   * or its return type will change.
   *
   * <p>
   * True will be returned for lambdas in non-invocation context as well as for lambdas in invocation context,
   * when invoked method is not overloaded or all overloads are 'lambda friendly'
   *
   * <p>
   *   Value-compatible lambda like {@code () -> foo() == true} can be converted to value-compatible AND void-compatible
   *   {@code () -> foo()} during simplification. This could lead to ambiguity during containing method call resolution and thus
   *   to the errors after applying the suggestion.
   * </p>
   *
   * @param lambda          a lambda whose body is going to be replaced
   * @param newBodySupplier replacement for lambda's body to check,
   *                        lazy computed for lambdas in invocation context only.
   *                        Replacement evaluated to {@code null} is treated as invalid overload
   */
  public static boolean isSafeLambdaBodyReplacement(@NotNull PsiLambdaExpression lambda, @NotNull Supplier<? extends PsiElement> newBodySupplier) {
    return isSafeLambdaReplacement(lambda, l -> {
      PsiElement oldBody = l.getBody();
      PsiElement newBody = newBodySupplier.get();
      if (oldBody == null || newBody == null) return null;
      oldBody.replace(newBody);
      return l;
    });
  }

  /**
   * {@link #isSafeLambdaBodyReplacement(PsiLambdaExpression, Supplier)} overload to test the same lambda body,
   * but with only return value {@code expression} changed to {@code replacement}
   * @param lambdaReturnExpression a return expression inside lambda body
   * @param replacement a replacement for return expression
   */
  public static boolean isSafeLambdaReturnValueReplacement(@NotNull PsiExpression lambdaReturnExpression,
                                                           @NotNull PsiExpression replacement) {
    if (lambdaReturnExpression.getParent() instanceof PsiReturnStatement || lambdaReturnExpression.getParent() instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(lambdaReturnExpression, PsiLambdaExpression.class, true, PsiMethod.class);
      if (lambdaExpression != null &&
          !isSafeLambdaBodyReplacement(lambdaExpression, () -> {
            PsiLambdaExpression lambdaExpression1 = PsiTreeUtil.getParentOfType(lambdaReturnExpression, PsiLambdaExpression.class);
            if (lambdaExpression1 == null) return null;
            PsiElement body = lambdaExpression1.getBody();
            if (body == null) return null;
            Object marker = new Object();
            PsiTreeUtil.mark(lambdaReturnExpression, marker);
            PsiElement copy = body.copy();
            PsiElement exprInReturn = PsiTreeUtil.releaseMark(copy, marker);
            if (exprInReturn == null) return null;
            if (exprInReturn == copy) {
              return exprInReturn.replace(replacement);
            }
            exprInReturn.replace(replacement);
            return copy;
          })) {
        return false;
      }
    }
    return true;
  }

  public static @NotNull PsiElement copyWithExpectedType(PsiElement expression, PsiType type) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    PsiTypeElement typeElement = factory.createTypeElement(type);
    if (!PsiUtil.isAvailable(JavaFeature.LAMBDA_EXPRESSIONS, expression)) {
      String canonicalText = type.getCanonicalText();
      final String arrayInitializer = "new " + canonicalText + "[]{0}";
      PsiNewExpression newExpr = (PsiNewExpression)factory.createExpressionFromText(arrayInitializer, expression);
      final PsiArrayInitializerExpression initializer = newExpr.getArrayInitializer();
      LOG.assertTrue(initializer != null);
      return initializer.getInitializers()[0].replace(expression);
    }

    final String callableWithExpectedType = "(java.util.concurrent.Callable<T>)() -> x";
    PsiTypeCastExpression typeCastExpr = (PsiTypeCastExpression)factory.createExpressionFromText(callableWithExpectedType, expression);
    PsiTypeElement castType = Objects.requireNonNull(typeCastExpr.getCastType());
    PsiJavaCodeReferenceElement callableRef = Objects.requireNonNull(castType.getInnermostComponentReferenceElement());
    Objects.requireNonNull(callableRef.getParameterList()).getTypeParameterElements()[0].replace(typeElement);
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)typeCastExpr.getOperand();
    LOG.assertTrue(lambdaExpression != null);
    PsiElement body = lambdaExpression.getBody();
    LOG.assertTrue(body instanceof PsiExpression);
    return body.replace(expression);
  }

  /**
   * Returns true if given lambda captures any variable or "this" reference.
   * @param lambda lambda to check
   * @return true if given lambda captures any variable or "this" reference.
   */
  public static boolean isCapturingLambda(PsiLambdaExpression lambda) {
    PsiElement body = lambda.getBody();
    if (body == null) return false;
    class CapturingLambdaVisitor extends JavaRecursiveElementWalkingVisitor {
      boolean capturing;

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression instanceof PsiMethodReferenceExpression) return;
        if (expression.getParent() instanceof PsiMethodCallExpression && expression.getQualifierExpression() != null) return;
        PsiElement target = expression.resolve();
        // variable/parameter/field/method/class are always PsiModifierListOwners
        if (target instanceof PsiModifierListOwner &&
            !((PsiModifierListOwner)target).hasModifierProperty(PsiModifier.STATIC) &&
            !PsiTreeUtil.isAncestor(lambda, target, true)) {
          if (target instanceof PsiClass && ((PsiClass)target).getContainingClass() == null) return;
          capturing = true;
          stopWalking();
        }
      }

      @Override
      public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
        capturing = true;
        stopWalking();
      }

      @Override
      public void visitThisExpression(@NotNull PsiThisExpression expression) {
        capturing = true;
        stopWalking();
      }
    }
    CapturingLambdaVisitor visitor = new CapturingLambdaVisitor();
    body.accept(visitor);
    return visitor.capturing;
  }

  /**
   * Resolves a functional interface class for given functional expression
   *
   * @param expression functional expression
   * @return resolved class or null if cannot be resolved
   */
  public static @Nullable PsiClass resolveFunctionalInterfaceClass(@NotNull PsiFunctionalExpression expression) {
    // First try to avoid substitution
    PsiType type = expression.getGroundTargetType(getFunctionalInterfaceType(expression, false));
    PsiClass actualClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (actualClass instanceof PsiTypeParameter) {
      // Rare case when function is resolved to a type parameter: perform substitution then
      return PsiUtil.resolveClassInClassTypeOnly(expression.getFunctionalInterfaceType());
    }
    return actualClass;
  }

  public static @Nullable String createLambdaParameterListWithFormalTypes(PsiType functionalInterfaceType,
                                                                          PsiLambdaExpression lambdaExpression,
                                                                          boolean checkApplicability) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final StringBuilder buf = new StringBuilder();
    buf.append("(");
    final PsiMethod interfaceMethod = getFunctionalInterfaceMethod(functionalInterfaceType);
    if (interfaceMethod == null) return null;
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] lambdaParameters = lambdaExpression.getParameterList().getParameters();
    if (parameters.length != lambdaParameters.length) return null;
    final PsiSubstitutor substitutor = getSubstitutor(interfaceMethod, resolveResult);
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter lambdaParameter = lambdaParameters[i];
      PsiTypeElement origTypeElement = lambdaParameter.getTypeElement();

      PsiType psiType;
      if (origTypeElement != null && !origTypeElement.isInferredType()) {
        psiType = origTypeElement.getType();
      } else {
        psiType = substitutor.substitute(parameters[i].getType());
        if (!PsiTypesUtil.isDenotableType(psiType, lambdaExpression)) return null;
      }
      psiType = PsiTypesUtil.removeExternalAnnotations(psiType);

      PsiAnnotation[] annotations = lambdaParameter.getAnnotations();
      for (PsiAnnotation annotation : annotations) {
        if (AnnotationTargetUtil.isTypeAnnotation(annotation)) continue;
        buf.append(annotation.getText()).append(' ');
      }
      buf.append(checkApplicability ? psiType.getPresentableText(true) : psiType.getCanonicalText(true))
        .append(" ")
        .append(lambdaParameter.getName());
      if (i < parameters.length - 1) {
        buf.append(", ");
      }
    }
    buf.append(")");
    return buf.toString();
  }

  public static @Nullable PsiParameterList specifyLambdaParameterTypes(PsiLambdaExpression lambdaExpression) {
    return specifyLambdaParameterTypes(lambdaExpression.getFunctionalInterfaceType(), lambdaExpression);
  }

  public static @Nullable PsiParameterList specifyLambdaParameterTypes(PsiType functionalInterfaceType,
                                                                       @NotNull PsiLambdaExpression lambdaExpression) {
    String typedParamList = createLambdaParameterListWithFormalTypes(functionalInterfaceType, lambdaExpression, false);
    if (typedParamList != null) {
      PsiParameterList paramListWithFormalTypes = JavaPsiFacade.getElementFactory(lambdaExpression.getProject())
        .createMethodFromText("void foo" + typedParamList, lambdaExpression).getParameterList();
      return (PsiParameterList)JavaCodeStyleManager.getInstance(lambdaExpression.getProject())
        .shortenClassReferences(lambdaExpression.getParameterList().replace(paramListWithFormalTypes));
    }
    return null;
  }

  /**
   * @return {@link PsiClass} or {@link PsiLambdaExpression} which contains passed {@code element}. 
   *         {@link PsiAnonymousClass} is skipped if {@code element} is located in the corresponding expression list
   */
  public static @Nullable PsiElement getContainingClassOrLambda(@NotNull PsiElement element) {
    PsiElement currentClass;
    while (true) {
      currentClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, PsiLambdaExpression.class);
      if (currentClass instanceof PsiAnonymousClass &&
          PsiTreeUtil.isAncestor(((PsiAnonymousClass)currentClass).getArgumentList(), element, false)) {
        element = currentClass;
      }
      else {
        return currentClass;
      }
    }
  }

  /**
   * Kind of error for functional interface
   */
  public enum FunctionalInterfaceStatus {
    VALID,
    NOT_INTERFACE,
    NO_ABSTRACT_METHOD,
    MULTIPLE_ABSTRACT_METHODS
  }

  /**
   * @param psiClass class to check whether it represents a functional interface
   * @return {@link FunctionalInterfaceStatus#VALID} if it's a valid functional interface, or other value
   * representing the kind of error
   */
  public static @NotNull FunctionalInterfaceStatus checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    List<HierarchicalMethodSignature> signatures = findFunctionCandidates(psiClass);
    if (signatures == null) return FunctionalInterfaceStatus.NOT_INTERFACE;
    if (signatures.isEmpty()) return FunctionalInterfaceStatus.NO_ABSTRACT_METHOD;
    if (signatures.size() == 1) {
      return FunctionalInterfaceStatus.VALID;
    }
    return FunctionalInterfaceStatus.MULTIPLE_ABSTRACT_METHODS;
  }
}