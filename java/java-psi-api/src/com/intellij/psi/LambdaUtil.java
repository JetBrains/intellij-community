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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: 7/17/12
 */
public class LambdaUtil {
  private static final Logger LOG = Logger.getInstance("#" + LambdaUtil.class.getName());
  public static ThreadLocal<Map<PsiMethodReferenceExpression, PsiType>> ourRefs = new ThreadLocal<Map<PsiMethodReferenceExpression, PsiType>>();
  public static ThreadLocal<Set<PsiParameterList>> ourParams = new ThreadLocal<Set<PsiParameterList>>();

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
    return TypeConversionUtil.getSuperClassSubstitutor(methodContainingClass, derivedClass, resolveResult.getSubstitutor());
  }

  public static boolean isValidLambdaContext(PsiElement context) {
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
    if (expression.getParameterList().getParametersCount() > 0 ||
        getFunctionalInterfaceReturnType(functionalInterfaceType) != PsiType.VOID) {   //todo check that void lambdas without params check
      if (!checkRawAcceptable(expression, functionalInterfaceType)) {
        return false;
      }
      return !dependsOnTypeParams(functionalInterfaceType, functionalInterfaceType, expression, null);
    }
    return true;
  }

  private static boolean checkRawAcceptable(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList && functionalInterfaceType instanceof PsiClassType && ((PsiClassType)functionalInterfaceType).isRaw()){
      return false;
    }
    return true;
  }

  @Nullable
  public static String checkInterfaceFunctional(PsiType functionalInterfaceType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (checkReturnTypeApplicable(resolveResult, aClass)) {
        return "No instance of type " + functionalInterfaceType.getPresentableText() + " exists so that lambda expression can be type-checked";
      }
      return checkInterfaceFunctional(aClass);
    }
    return null;
  }

  private static boolean checkReturnTypeApplicable(PsiClassType.ClassResolveResult resolveResult, final PsiClass aClass) {
    final MethodSignature methodSignature = getFunction(aClass);
    if (methodSignature == null) return false;

    for (PsiTypeParameter parameter : aClass.getTypeParameters()) {
      if (parameter.getExtendsListTypes().length == 0) continue;
      boolean depends = false;
      final PsiType substitution = resolveResult.getSubstitutor().substitute(parameter);
      if (substitution instanceof PsiWildcardType && !((PsiWildcardType)substitution).isBounded()) {
        for (PsiType paramType : methodSignature.getParameterTypes()) {
          if (depends(paramType, parameter, new TypeParamsChecker((PsiMethod)null, aClass){
            @Override
            public boolean startedInference() {
              return true;
            }
          })) {
            depends = true;
            break;
          }
        }
        if (!depends) return true;
      }
    }
    return false;
  }

  @Nullable
  public static String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
    final List<MethodSignature> signatures = findFunctionCandidates(psiClass);
    if (signatures == null) return "Target type of a lambda conversion must be an interface";
    if (signatures.isEmpty()) return "No target method found";
    return signatures.size() == 1 ? null : "Multiple non-overriding abstract methods found";
  }
  
  public static String checkReturnTypeCompatible(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceReturnType) {
    if (functionalInterfaceReturnType == PsiType.VOID) {
      final PsiElement body = lambdaExpression.getBody();
      if (body instanceof PsiCodeBlock) {
        if (!lambdaExpression.getReturnExpressions().isEmpty()) return "Unexpected return value";
      } else if (body instanceof PsiExpression) {
        final PsiType type = ((PsiExpression)body).getType();
        if (type != PsiType.VOID) {
          return "Incompatible return type " + (type == PsiType.NULL || type == null ? "<null>" : type.getPresentableText()) +" in lambda expression";
        }
      }
    } else if (functionalInterfaceReturnType != null) {
      final List<PsiExpression> returnExpressions = lambdaExpression.getReturnExpressions();
      for (PsiExpression expression : returnExpressions) {
        final PsiType expressionType = expression.getType();
        if (expressionType != null && !functionalInterfaceReturnType.isAssignableFrom(expressionType)) {
          return "Incompatible return type " + expressionType.getPresentableText() + " in lambda expression";
        }
      }
      if (lambdaExpression.getReturnStatements().size() > returnExpressions.size() || returnExpressions.isEmpty() && !lambdaExpression.isVoidCompatible()) {
        return "Missing return value";
      }
    }
    return null;
  }

  public static boolean isAcceptable(PsiLambdaExpression lambdaExpression, final PsiType leftType, boolean checkReturnType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(leftType);
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
        return checkReturnTypeCompatible((PsiLambdaExpression)localVariable.getInitializer(), methodReturnType) == null;
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
    if (psiClass instanceof PsiAnonymousClass) {
      psiClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)psiClass).getBaseClassType());
    }
    if (psiClass != null && psiClass.isInterface()) {
      final List<MethodSignature> methods = new ArrayList<MethodSignature>();
      final Collection<HierarchicalMethodSignature> visibleSignatures = psiClass.getVisibleSignatures();
      for (HierarchicalMethodSignature signature : visibleSignatures) {
        final PsiMethod psiMethod = signature.getMethod();
        if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
        if (!overridesPublicObjectMethod(psiMethod) && !psiMethod.isExtensionMethod()) {
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

  public static boolean dependsOnTypeParams(PsiType type, PsiLambdaExpression expr) {
    return dependsOnTypeParams(type, expr, null);
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiLambdaExpression expr,
                                            PsiTypeParameter param2Check) {
    return depends(type, param2Check, new TypeParamsChecker(expr));
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiType functionalInterfaceType,
                                            PsiElement lambdaExpression,
                                            PsiTypeParameter param2Check) {
    return depends(type, param2Check, new TypeParamsChecker(lambdaExpression,
                                                            PsiUtil.resolveClassInType(functionalInterfaceType)));
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiClass aClass,
                                            PsiMethod aMethod) {
    return depends(type, null, new TypeParamsChecker(aMethod, aClass));
  }

  private static boolean depends(PsiType type, PsiTypeParameter param2Check, TypeParamsChecker visitor) {
    if (!visitor.startedInference()) return false;
    final Boolean accept = type.accept(visitor);
    if (param2Check != null) {
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

        if (usedParamIdx > -1 && dependsOnTypeParams(subst.substitute(methodParameters[usedParamIdx].getType()), functionalInterfaceType, lambdaExpression, typeParam)) {
          independent[0] = false;
        }
      }
    });
    return independent[0];
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
      return ((PsiTypeCastExpression)parent).getType();
    }
    else if (parent instanceof PsiVariable) {
      return ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      return lExpression.getType();
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final int lambdaIdx = getLambdaIdx(expressionList, expression);
      if (lambdaIdx > -1) {

        if (!tryToSubstitute) {
          final Map<PsiElement, PsiMethod> currentMethodCandidates = MethodCandidateInfo.CURRENT_CANDIDATE.get();
          final PsiMethod method = currentMethodCandidates != null ? currentMethodCandidates.get(parent) : null;
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            return lambdaIdx < parameters.length ? parameters[lambdaIdx].getType() : null;
          }
        }

        final PsiElement gParent = expressionList.getParent();
        if (gParent instanceof PsiCallExpression) {
          final PsiCallExpression contextCall = (PsiCallExpression)gParent;
          final JavaResolveResult resolveResult = contextCall.resolveMethodGenerics();
            final PsiElement resolve = resolveResult.getElement();
            if (resolve instanceof PsiMethod) {
              final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
              if (lambdaIdx < parameters.length) {
                if (!tryToSubstitute) return parameters[lambdaIdx].getType();
                return PsiResolveHelper.ourGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
                  @Override
                  public PsiType compute() {
                    return resolveResult.getSubstitutor().substitute(parameters[lambdaIdx].getType());
                  }
                });
              }
            }
            return null;
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        return method.getReturnType();
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      final PsiType parentInterfaceType = ((PsiLambdaExpression)parent).getFunctionalInterfaceType();
      if (parentInterfaceType != null) {
        return getFunctionalInterfaceReturnType(parentInterfaceType);
      }
    }
    return null;
  }

  public static PsiType getLambdaParameterType(PsiParameter param) {
    final PsiElement paramParent = param.getParent();
    if (paramParent instanceof PsiParameterList) {
      final int parameterIndex = ((PsiParameterList)paramParent).getParameterIndex(param);
      if (parameterIndex > -1) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param, PsiLambdaExpression.class);
        if (lambdaExpression != null) {

          Set<PsiParameterList> currentStack = ourParams.get();
          if (currentStack == null) {
            currentStack = new HashSet<PsiParameterList>();
            ourParams.set(currentStack);
          }

          final PsiParameterList parameterList = lambdaExpression.getParameterList();
          final boolean add = currentStack.add(parameterList);
          try {
            PsiType type = getFunctionalInterfaceType(lambdaExpression, true);
            if (type == null) {
              type = getFunctionalInterfaceType(lambdaExpression, false);
            }
            final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(type);
            if (resolveResult != null) {
              final PsiMethod method = getFunctionalInterfaceMethod(type);
              if (method != null) {
                final PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameterIndex < parameters.length) {
                  final PsiType psiType = getSubstitutor(method, resolveResult).substitute(parameters[parameterIndex].getType());
                  if (!dependsOnTypeParams(psiType, type, lambdaExpression, null)) {
                    return GenericsUtil.eliminateWildcards(psiType);
                  }
                }
              }
            }
          }
          finally {
            if (add) currentStack.remove(parameterList);
          }
        }
      }
    }
    return new PsiLambdaParameterType(param);
  }

  public static boolean insertSemicolonAfter(PsiLambdaExpression lambdaExpression) {
     if (lambdaExpression.getBody() instanceof PsiCodeBlock) {
       return true;
     }
    if (insertSemicolon(lambdaExpression.getParent())) {
      return false;
    }
    return true;
  }

  public static boolean insertSemicolon(PsiElement parent) {
    return parent instanceof PsiExpressionList || parent instanceof PsiExpression;
  }

  public static boolean isValidQualifier(PsiMethodReferenceExpression expression) {
    final PsiElement referenceNameElement = expression.getReferenceNameElement();
    if (referenceNameElement instanceof PsiKeyword) {
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        return true;
      }
      if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
        return true;
      }
    }
    return false;
  }
  
  public static boolean isAcceptable(@Nullable final PsiMethodReferenceExpression methodReferenceExpression, PsiType left) {
    if (methodReferenceExpression == null) return false;
    Map<PsiMethodReferenceExpression, PsiType> map = ourRefs.get();
    if (map == null) {
      map = new HashMap<PsiMethodReferenceExpression, PsiType>();
      ourRefs.set(map);
    }

    final JavaResolveResult result;
    try {
      if (map.put(methodReferenceExpression, left) != null) {
        return false;
      }
      result = methodReferenceExpression.advancedResolve(false);
    }
    finally {
      map.remove(methodReferenceExpression);
    }

    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(left);
    final PsiMethod method = getFunctionalInterfaceMethod(resolveResult);
    if (method != null) {
      final Ref<PsiClass> classRef = new Ref<PsiClass>();
      final Ref<PsiSubstitutor> substRef = new Ref<PsiSubstitutor>();
      methodReferenceExpression.process(classRef, substRef);
      final PsiElement resolve = result.getElement();
      if (resolve instanceof PsiMethod) {
        final MethodSignature signature1 = method.getSignature(resolveResult.getSubstitutor());
        PsiSubstitutor subst = PsiSubstitutor.EMPTY;
        subst = subst.putAll(substRef.get());
        subst = subst.putAll(result.getSubstitutor());
        final MethodSignature signature2 = ((PsiMethod)resolve).getSignature(subst);

        final PsiType interfaceReturnType = getFunctionalInterfaceReturnType(left);
        PsiType methodReturnType = subst.substitute(((PsiMethod)resolve).getReturnType());
        if (interfaceReturnType != null && interfaceReturnType != PsiType.VOID) {
          if (methodReturnType == null) {
            methodReturnType = JavaPsiFacade.getElementFactory(methodReferenceExpression.getProject()).createType(((PsiMethod)resolve).getContainingClass(), subst);
          }
          if (!TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType, false)) return false;
        }
        if (areAcceptable(signature1, signature2, classRef.get(), substRef.get(), ((PsiMethod)resolve).isVarArgs())) return true;
      } else if (resolve instanceof PsiClass) {
        final PsiType interfaceReturnType = getFunctionalInterfaceReturnType(left);
        if (interfaceReturnType != null) {
          if (interfaceReturnType == PsiType.VOID) return true;
          final PsiClassType classType = JavaPsiFacade.getElementFactory(methodReferenceExpression.getProject()).createType((PsiClass)resolve, result.getSubstitutor());
          if (TypeConversionUtil.isAssignable(interfaceReturnType, classType, false)) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length == 0) return true;
            if (parameters.length == 1) {
              if (isReceiverType(resolveResult.getSubstitutor().substitute(parameters[0].getType()), classRef.get(), substRef.get())) return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isReceiverType(@Nullable PsiClass aClass, @Nullable PsiClass containingClass) {
    while (containingClass != null) {
      if (InheritanceUtil.isInheritorOrSelf(aClass, containingClass, true)) return true;
      containingClass = containingClass.getContainingClass();
    }
    return false;
  }

  public static boolean isReceiverType(PsiType receiverType, @Nullable PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(GenericsUtil.eliminateWildcards(receiverType));
    final PsiClass receiverClass = resolveResult.getElement();
    if (receiverClass != null && isReceiverType(receiverClass, containingClass)) {
      LOG.assertTrue(containingClass != null);
      return resolveResult.getSubstitutor().equals(psiSubstitutor) ||
             PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor) ||
             PsiUtil.isRawSubstitutor(receiverClass, resolveResult.getSubstitutor());
    } 
    return false;
  }
  
  public static boolean isReceiverType(PsiType functionalInterfaceType, PsiClass containingClass, @Nullable PsiMethod referencedMethod) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final MethodSignature function = getFunction(resolveResult.getElement());
    if (function != null) {
      final int interfaceMethodParamsLength = function.getParameterTypes().length;
      if (interfaceMethodParamsLength > 0) {
        final PsiType firstParamType = resolveResult.getSubstitutor().substitute(function.getParameterTypes()[0]);
        boolean isReceiver = isReceiverType(firstParamType,
                                            containingClass, PsiUtil.resolveGenericsClassInType(firstParamType).getSubstitutor());
        if (isReceiver) {
          if (referencedMethod == null){
            if (interfaceMethodParamsLength == 1) return true;
            return false;
          }
          if (referencedMethod.getParameterList().getParametersCount() != interfaceMethodParamsLength - 1) {
            return false;
          }
          return true;
        }
      }
    }
    return false;
  }
  
  public static boolean areAcceptable(MethodSignature signature1,
                                      MethodSignature signature2,
                                      PsiClass psiClass,
                                      PsiSubstitutor psiSubstitutor, 
                                      boolean isVarargs) {
    int offset = 0;
    final PsiType[] signatureParameterTypes1 = signature1.getParameterTypes();
    final PsiType[] signatureParameterTypes2 = signature2.getParameterTypes();
    if (signatureParameterTypes1.length != signatureParameterTypes2.length) {
      if (signatureParameterTypes1.length == signatureParameterTypes2.length + 1) {
        if (isReceiverType(signatureParameterTypes1[0], psiClass, psiSubstitutor)) {
          offset++;
        }
        else if (!isVarargs){
          return false;
        }
      }
      else if (!isVarargs) {
        return false;
      }
    }

    final int min = Math.min(signatureParameterTypes2.length, signatureParameterTypes1.length);
    for (int i = 0; i < min; i++) {
      final PsiType type1 = psiSubstitutor.substitute(GenericsUtil.eliminateWildcards(signatureParameterTypes1[offset + i]));
      if (isVarargs && i == min - 1) {
        if (!TypeConversionUtil.isAssignable(((PsiArrayType)signatureParameterTypes2[i]).getComponentType(), type1) && 
            !TypeConversionUtil.isAssignable(signatureParameterTypes2[i], type1)) {
          return false;
        }
      }
      else {
        if (!TypeConversionUtil.isAssignable(signatureParameterTypes2[i], psiSubstitutor.substitute(GenericsUtil.eliminateWildcards(type1)))) {
          return false;
        }
      }
    }
    return true;
  }

  public static PsiLocalVariable createMethodReferenceExpressionAccording2Type(PsiMethodReferenceExpression methodReferenceExpression,
                                                                               PsiType leftType) {
    final String uniqueVarName =
      JavaCodeStyleManager.getInstance(methodReferenceExpression.getProject())
        .suggestUniqueVariableName("l", methodReferenceExpression, true);
    final PsiStatement assignmentFromText = JavaPsiFacade.getElementFactory(methodReferenceExpression.getProject())
      .createStatementFromText(leftType.getCanonicalText() + " " + uniqueVarName + " = " + methodReferenceExpression.getText(),
                               methodReferenceExpression);
    return (PsiLocalVariable)((PsiDeclarationStatement)assignmentFromText).getDeclaredElements()[0];
  }

  public static void processMethodReferenceReturnType(List<CandidateInfo> conflicts, int functionalInterfaceIdx) {
    final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);
    for (int i = 1; i < newConflictsArray.length; i++) {
      final CandidateInfo method = newConflictsArray[i];
      final PsiType interfaceReturnType = getReturnType(functionalInterfaceIdx, method);
      for (int j = 0; j < i; j++) {
        final CandidateInfo conflict = newConflictsArray[j];
        assert conflict != method;
        final PsiType interfaceReturnType1 = getReturnType(functionalInterfaceIdx, conflict);
        if (interfaceReturnType != null && interfaceReturnType1 != null && !Comparing.equal(interfaceReturnType, interfaceReturnType1)) {
          int moreSpecific = isMoreSpecific(interfaceReturnType, interfaceReturnType1);
          if (moreSpecific > 0) {
            conflicts.remove(method);
            break;
          }
          else if (moreSpecific < 0) {
            conflicts.remove(conflict);
          }
        }
      }
    }
  }

  private static int isMoreSpecific(PsiType returnType, PsiType returnType1) {
    final PsiClassType.ClassResolveResult r = PsiUtil.resolveGenericsClassInType(returnType);
    final PsiClass rClass = r.getElement();
    final PsiClassType.ClassResolveResult r1 = PsiUtil.resolveGenericsClassInType(returnType1);
    final PsiClass rClass1 = r1.getElement();
    if (rClass != null && rClass1 != null) {
      if (rClass == rClass1) {
        int moreSpecific = 0;
        for (PsiTypeParameter parameter : rClass.getTypeParameters()) {
          final PsiType t = r.getSubstitutor().substituteWithBoundsPromotion(parameter);
          final PsiType t1 = r1.getSubstitutor().substituteWithBoundsPromotion(parameter);
          if (t == null || t1 == null) continue;
          if (t1.isAssignableFrom(t) && !GenericsUtil.eliminateWildcards(t1).equals(t)) {
            if (moreSpecific == 1) {
              return 0;
            }
            moreSpecific = -1;
          }
          else if (t.isAssignableFrom(t1) && !GenericsUtil.eliminateWildcards(t).equals(t1)) {
            if (moreSpecific == -1) {
              return 0;
            }
            moreSpecific = 1;
          }
          else {
            return 0;
          }
        }
        return moreSpecific;
      }
      else if (rClass1.isInheritor(rClass, true)) {
        return 1;
      }
      else if (rClass.isInheritor(rClass1, true)) {
        return -1;
      }
    }
    return 0;
  }

  @Nullable
  private static PsiType getReturnType(int functionalTypeIdx, CandidateInfo method) {
    final PsiParameter[] methodParameters = ((PsiMethod)method.getElement()).getParameterList().getParameters();
    final PsiParameter param = functionalTypeIdx < methodParameters.length ? methodParameters[functionalTypeIdx] : methodParameters[methodParameters.length - 1];
    final PsiType functionalInterfaceType = method.getSubstitutor().substitute(param.getType());
    return getFunctionalInterfaceReturnType(functionalInterfaceType);
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
      if (constraint != PsiType.NULL) {
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

  private static class TypeParamsChecker extends PsiTypeVisitor<Boolean> {
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
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiExpressionList) {
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiCallExpression) {
          final Map<PsiElement, PsiMethod> map = MethodCandidateInfo.CURRENT_CANDIDATE.get();
          myMethod = map != null ? map.get(parent) : null;
          if (myMethod == null) {
            myMethod = ((PsiCallExpression)gParent).resolveMethod();
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

    public boolean used(PsiTypeParameter parameter) {
      return myUsedTypeParams.contains(parameter);
    }
  }
}
