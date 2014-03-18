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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
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
  public static ThreadLocal<Map<PsiElement, PsiType>> ourFunctionTypes = new ThreadLocal<Map<PsiElement, PsiType>>();
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

  public static PsiMethod getFunctionalInterfaceMethod(@Nullable PsiElement element) {
    if (element instanceof PsiLambdaExpression || element instanceof PsiMethodReferenceExpression) {
      final PsiType samType = element instanceof PsiLambdaExpression
                              ? ((PsiLambdaExpression)element).getFunctionalInterfaceType()
                              : ((PsiMethodReferenceExpression)element).getFunctionalInterfaceType();
      return getFunctionalInterfaceMethod(samType);
    }
    return null;
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

  public static boolean isFunctionalType(PsiType type) {
    if (type instanceof PsiIntersectionType) {
      for (PsiType type1 : ((PsiIntersectionType)type).getConjuncts()) {
        if (isFunctionalType(type1)) return true;
      }
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(GenericsUtil.eliminateWildcards(type));
    final PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return false;
      final List<MethodSignature> signatures = findFunctionCandidates(aClass);
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
    if (hasParams || getFunctionalInterfaceReturnType(functionalInterfaceType) != PsiType.VOID) {   //todo check that void lambdas without params check
      
      return !dependsOnTypeParams(functionalInterfaceType, functionalInterfaceType, expression);
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
  public static List<MethodSignature> findFunctionCandidates(PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      psiClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)psiClass).getBaseClassType());
    }
    if (psiClass != null && psiClass.isInterface() && !psiClass.isAnnotationType()) {
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
          final JavaResolveResult resolveResult = contextCall.resolveMethodGenerics();
            final PsiElement resolve = resolveResult.getElement();
            if (resolve instanceof PsiMethod) {
              final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
              final int finalLambdaIdx = adjustLambdaIdx(lambdaIdx, (PsiMethod)resolve, parameters);
              if (finalLambdaIdx < parameters.length) {
                if (!tryToSubstitute) return getNormalizedType(parameters[finalLambdaIdx]);
                final Map<PsiElement, PsiType> map = ourFunctionTypes.get();
                if (map != null) {
                  final PsiType type = map.get(expression);
                  if (type != null) {
                    return type;
                  }
                }
                return PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
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
                                                                   @Nullable PsiElement scope, @NotNull LanguageLevel languageLevel) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
        return qualifierExpression == null && (scope instanceof PsiImportStaticStatement || PsiTreeUtil.isAncestor(containingClass, methodReferenceExpression, true))||
               qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() == containingClass;
      }
    }
    return true;
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
