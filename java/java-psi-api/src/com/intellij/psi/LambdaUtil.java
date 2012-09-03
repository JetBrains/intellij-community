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

  @Nullable
  public static PsiType getFunctionalInterfaceReturnType(PsiLambdaExpression expr) {
    return getFunctionalInterfaceReturnType(expr.getFunctionalInterfaceType());
  }
  
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
           context instanceof PsiParenthesizedExpression ||
           context instanceof PsiArrayInitializerExpression;
  }

  public static boolean isLambdaFullyInferred(PsiLambdaExpression expression, PsiType functionalInterfaceType) {
    if (expression.getParameterList().getParametersCount() > 0 || getFunctionalInterfaceReturnType(functionalInterfaceType) != PsiType.VOID) {   //todo check that void lambdas without params check
      if (functionalInterfaceType instanceof PsiClassType && ((PsiClassType)functionalInterfaceType).isRaw()) return false;
      return !dependsOnTypeParams(functionalInterfaceType, expression);
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
          if (methodParameterType instanceof PsiPrimitiveType) return methodParameterType.equals(lambdaFormalType);
          return false;
        }

        if (!TypeConversionUtil.erasure(lambdaFormalType)
          .isAssignableFrom(TypeConversionUtil.erasure(GenericsUtil.eliminateWildcards(resolveResult.getSubstitutor().substitute(methodSignature.getSubstitutor().substitute(methodParameterType)))))) {
          return false;
        }
      }
    }
    LOG.assertTrue(psiClass != null);
    PsiType methodReturnType = getReturnType(psiClass, methodSignature);
    if (methodReturnType != null) {
      methodReturnType = resolveResult.getSubstitutor().substitute(methodSignature.getSubstitutor().substitute(methodReturnType));
      return checkReturnTypeCompatible(lambdaExpression, methodReturnType) == null;
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
      final Collection<HierarchicalMethodSignature> visibleSignatures = psiClass.getVisibleSignatures();
      for (HierarchicalMethodSignature signature : visibleSignatures) {
        final PsiMethod psiMethod = signature.getMethod();
        if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
        if (!overridesPublicObjectMethod(psiMethod) && !PsiUtil.isExtensionMethod(psiMethod)) {
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
    return dependsOnTypeParams(type, expr, null);
  }

  public static boolean dependsOnTypeParams(PsiType type,
                                            PsiLambdaExpression expr,
                                            PsiTypeParameter param2Check) {
    return depends(type, param2Check, new TypeParamsChecker(expr));
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
                                                    final PsiExpression expression) {
    if (expression instanceof PsiCallExpression && ((PsiCallExpression)expression).getTypeArguments().length > 0) return true;
    if (expression instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)expression).getClassOrAnonymousClassReference();
      if (classReference != null && classReference.getTypeParameters().length > 0) return true;
    }
    final PsiParameter[] lambdaParams = lambdaExpression.getParameterList().getParameters(); 
    if (lambdaParams.length != methodParameters.length) return false;
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

  @Nullable
  public static PsiType getFunctionalInterfaceType(PsiLambdaExpression expression, final boolean tryToSubstitute) {
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiTypeCastExpression) {
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
              if (!tryToSubstitute) {
                return parameters[lambdaIdx].getType(); 
              }
              return PsiResolveHelper.ourGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
                @Override
                public PsiType compute() {
                  return resolveResult.getSubstitutor().substitute(parameters[lambdaIdx].getType());
                }
              });
            }
          }
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
              final PsiType psiType = resolveResult.getSubstitutor().substitute(parameters[parameterIndex].getType());
              if (!dependsOnTypeParams(psiType, lambdaExpression)) {
                if (psiType instanceof PsiWildcardType) {
                  final PsiType bound = ((PsiWildcardType)psiType).getBound();
                  if (bound != null) {
                    return bound;
                  }
                }
                return psiType;
              }
            }
          }
        }
      }
    }
    return new PsiLambdaParameterType(param);
  }

  private static class TypeParamsChecker extends PsiTypeVisitor<Boolean> {
    private PsiMethod myMethod;
    private final PsiClass myClass;
    private final Set<PsiTypeParameter> myUsedTypeParams = new HashSet<PsiTypeParameter>(); 

    private TypeParamsChecker(PsiMethod method, PsiClass aClass) {
      myMethod = method;
      myClass = aClass;
    }

    public TypeParamsChecker(PsiLambdaExpression expression) {
      myClass = PsiUtil.resolveGenericsClassInType(getFunctionalInterfaceType(expression, false)).getElement();
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiExpressionList) {
        final PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiCallExpression) {
          myMethod = ((PsiCallExpression)gParent).resolveMethod();
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
