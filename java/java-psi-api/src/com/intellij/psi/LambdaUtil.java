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
import com.intellij.openapi.util.Computable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
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

  static boolean depends(PsiType type, PsiTypeParameter param2Check, TypeParamsChecker visitor) {
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

  public static boolean notInferredType(PsiType typeByExpression) {
    return typeByExpression instanceof PsiMethodReferenceType || typeByExpression instanceof PsiLambdaExpressionType || typeByExpression instanceof PsiLambdaParameterType;
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
