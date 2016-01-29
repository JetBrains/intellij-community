/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: 7/17/12
 */
public class LambdaUtil {
  public static ThreadLocal<Map<PsiElement, PsiType>> ourFunctionTypes = new ThreadLocal<Map<PsiElement, PsiType>>();
  private static final Logger LOG = Logger.getInstance("#" + LambdaUtil.class.getName());

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
  public static PsiMethod getFunctionalInterfaceMethod(PsiClassType.ClassResolveResult result) {
    return getFunctionalInterfaceMethod(result.getElement());
  }

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
      final PsiType substitute = superClassSubstitutor.substitute(param);
      if (substitute != null) {
        initialSubst = initialSubst.put(param, initialSubst.substitute(substitute));
      }
    }
    return initialSubst;
  }

  public static boolean isFunctionalType(PsiType type) {
    if (type instanceof PsiIntersectionType) {
      return extractFunctionalConjunct((PsiIntersectionType)type) != null;
    }
    return isFunctionalClass(PsiUtil.resolveGenericsClassInType(type).getElement());
  }

  @Contract("null -> false")
  public static boolean isFunctionalClass(PsiClass aClass) {
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return false;
      final List<HierarchicalMethodSignature> signatures = findFunctionCandidates(aClass);
      return signatures != null && signatures.size() == 1;
    }
    return false;
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
           context instanceof PsiConditionalExpression && PsiTreeUtil.getParentOfType(context, PsiTypeCastExpression.class) == null;
  }

  public static boolean isLambdaFullyInferred(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    final boolean hasParams = expression.getParameterList().getParametersCount() > 0;
    if (hasParams || !PsiType.VOID.equals(getFunctionalInterfaceReturnType(functionalInterfaceType))) {   //todo check that void lambdas without params check
      
      return !dependsOnTypeParams(functionalInterfaceType, functionalInterfaceType, expression);
    }
    return true;
  }

  @Nullable
  public static MethodSignature getFunction(PsiClass psiClass) {
    if (psiClass == null) return null;
    final List<HierarchicalMethodSignature> functions = findFunctionCandidates(psiClass);
    if (functions != null && functions.size() == 1) {
      return functions.get(0);
    }
    return null;
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

  @Nullable
  private static List<HierarchicalMethodSignature> hasSubsignature(List<HierarchicalMethodSignature> signatures) {
    for (HierarchicalMethodSignature signature : signatures) {
      boolean subsignature = true;
      for (HierarchicalMethodSignature methodSignature : signatures) {
        if (!signature.equals(methodSignature)) {
          if (!MethodSignatureUtil.isSubsignature(signature, methodSignature) &&
              !skipMethod(signature, methodSignature)) {
            subsignature = false;
            break;
          }
        }
      }
      if (subsignature) return Collections.singletonList(signature);
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

  @Nullable
  public static List<HierarchicalMethodSignature> findFunctionCandidates(PsiClass psiClass) {
    if (psiClass != null && psiClass.isInterface() && !psiClass.isAnnotationType()) {
      final List<HierarchicalMethodSignature> methods = new ArrayList<HierarchicalMethodSignature>();
      final Collection<HierarchicalMethodSignature> visibleSignatures = psiClass.getVisibleSignatures();
      for (HierarchicalMethodSignature signature : visibleSignatures) {
        final PsiMethod psiMethod = signature.getMethod();
        if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (!overridesPublicObjectMethod(signature)) {
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
      // provided the intersection induces a functional interface (§9.8).
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
          final JavaResolveResult resolveResult = properties != null ? properties.getInfo() : contextCall.resolveMethodGenerics();
          return getSubstitutedType(expression, tryToSubstitute, lambdaIdx, resolveResult);
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiElement gParent = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, PsiMethod.class);
      if (gParent instanceof PsiLambdaExpression) {
        return getFunctionalInterfaceTypeByContainingLambda((PsiLambdaExpression)gParent);
      } else if (gParent instanceof PsiMethod) {
        return ((PsiMethod)gParent).getReturnType();
      }
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
        return PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, !MethodCandidateInfo.isOverloadCheck(), new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return resolveResult.getSubstitutor().substitute(getNormalizedType(parameters[finalLambdaIdx]));
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
          final Set<PsiType> types = new HashSet<PsiType>();
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
    for (PsiType conjunctType : type.getConjuncts()) {
      final PsiMethod interfaceMethod = getFunctionalInterfaceMethod(conjunctType);
      if (interfaceMethod != null) {
        if (conjunct != null && !conjunct.equals(conjunctType)) return null;
        conjunct = conjunctType;
      }
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

  public static boolean notInferredType(PsiType typeByExpression) {
    return typeByExpression instanceof PsiMethodReferenceType || typeByExpression instanceof PsiLambdaExpressionType || typeByExpression instanceof PsiLambdaParameterType;
  }

  public static boolean isLambdaReturnExpression(PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof PsiLambdaExpression ||
           parent instanceof PsiReturnStatement && PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiMethod.class) != null;
  }
  
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
    final List<PsiExpression> result = new ArrayList<PsiExpression>();
    for (PsiReturnStatement returnStatement : getReturnStatements(lambdaExpression)) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        result.add(returnValue);
      }
    }
    return result;
  }

  public static boolean isValidQualifier4InterfaceStaticMethodCall(@NotNull PsiMethod method,
                                                                   @NotNull PsiReferenceExpression methodReferenceExpression,
                                                                   @Nullable PsiElement scope, @NotNull LanguageLevel languageLevel) {
    return getInvalidQualifier4StaticInterfaceMethodMessage(method, methodReferenceExpression, scope, languageLevel) == null;
  }

  @Nullable
  public static String getInvalidQualifier4StaticInterfaceMethodMessage(@NotNull PsiMethod method,
                                                                        @NotNull PsiReferenceExpression methodReferenceExpression,
                                                                        @Nullable PsiElement scope, @NotNull LanguageLevel languageLevel) {
    final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass != null && containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
      if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        return "Static interface method invocations are not supported at this language level";
      }

      if (qualifierExpression == null && (scope instanceof PsiImportStaticStatement || PsiTreeUtil.isAncestor(containingClass, methodReferenceExpression, true))) {
        return null;
      }
      if (qualifierExpression instanceof PsiReferenceExpression) {
        final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve == containingClass) {
          return null;
        }
        
        if (resolve instanceof PsiTypeParameter) {
          final Set<PsiClass> classes = new HashSet<PsiClass>();
          for (PsiClassType type : ((PsiTypeParameter)resolve).getExtendsListTypes()) {
            final PsiClass aClass = type.resolve();
            if (aClass != null) {
              classes.add(aClass);
            }
          }
          
          if (classes.size() == 1 && classes.contains(containingClass)) {
            return null;
          }
        }
      }
      return "Static method may be invoked on containing interface class only";
    }
    return null;
  }

  //JLS 14.8 Expression Statements
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
      map = new HashMap<PsiElement, PsiType>();
      ourFunctionTypes.set(map);
    }
    return map;
  }

  public static String checkReturnTypeCompatible(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceReturnType) {
    if (PsiType.VOID.equals(functionalInterfaceReturnType)) {
      final PsiElement body = lambdaExpression.getBody();
      if (body instanceof PsiCodeBlock) {
        if (!getReturnExpressions(lambdaExpression).isEmpty()) return "Unexpected return value";
      } else if (body instanceof PsiExpression) {
        final PsiType type = ((PsiExpression)body).getType();
        try {
          if (!PsiUtil.isStatement(JavaPsiFacade.getElementFactory(body.getProject()).createStatementFromText(body.getText(), body))) {
            return "Bad return type in lambda expression: " + (type == PsiType.NULL || type == null ? "<null>" : type.getPresentableText()) + " cannot be converted to void";
          }
        }
        catch (IncorrectOperationException ignore) {
        }
      }
    } else if (functionalInterfaceReturnType != null) {
      final List<PsiExpression> returnExpressions = getReturnExpressions(lambdaExpression);
      for (final PsiExpression expression : returnExpressions) {
        final PsiType expressionType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return expression.getType();
          }
        });
        if (expressionType != null && !functionalInterfaceReturnType.isAssignableFrom(expressionType)) {
          return "Bad return type in lambda expression: " + expressionType.getPresentableText() + " cannot be converted to " + functionalInterfaceReturnType.getPresentableText();
        }
      }
      if (getReturnStatements(lambdaExpression).length > returnExpressions.size() || returnExpressions.isEmpty() && !lambdaExpression.isVoidCompatible()) {
        return "Missing return value";
      }
    }
    return null;
  }

  @Nullable
  public static PsiType getLambdaParameterFromType(PsiType functionalInterfaceType, int parameterIndex) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    if (resolveResult != null) {
      final PsiMethod method = getFunctionalInterfaceMethod(functionalInterfaceType);
      if (method != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameterIndex < parameters.length) {
          return getSubstitutor(method, resolveResult).substitute(parameters[parameterIndex].getType());
        }
      }
    }
    return null;
  }

  public static class TypeParamsChecker extends PsiTypeVisitor<Boolean> {
    private PsiMethod myMethod;
    private final PsiClass myClass;
    public final Set<PsiTypeParameter> myUsedTypeParams = new HashSet<PsiTypeParameter>();

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
