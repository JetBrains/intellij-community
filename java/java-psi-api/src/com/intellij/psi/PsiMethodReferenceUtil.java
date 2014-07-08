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
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User: anna
 */
public class PsiMethodReferenceUtil {
  public static ThreadLocal<Map<PsiMethodReferenceExpression, PsiType>> ourRefs = new ThreadLocal<Map<PsiMethodReferenceExpression, PsiType>>();

  public static final Logger LOG = Logger.getInstance("#" + PsiMethodReferenceUtil.class.getName());

  public static boolean hasReceiver(PsiType[] parameterTypes, QualifierResolveResult qualifierResolveResult, PsiMethodReferenceExpression methodRef) {
    if (parameterTypes.length > 0 && 
        !methodRef.isConstructor() &&
        isReceiverType(parameterTypes[0], qualifierResolveResult.getContainingClass(), qualifierResolveResult.getSubstitutor()) &&
        isStaticallyReferenced(methodRef)) {
      return true;
    }
    return false;
  }

  public static String checkReturnType(PsiMethodReferenceExpression expression, JavaResolveResult result, PsiType functionalInterfaceType) {
    final PsiElement resolve = result.getElement();
    if (resolve instanceof PsiMethod) {
      PsiSubstitutor subst = result.getSubstitutor();

      final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);

      PsiType returnType = PsiTypesUtil.patchMethodGetClassReturnType(expression, expression,
                                                                      (PsiMethod)resolve, null,
                                                                      PsiUtil.getLanguageLevel(expression));
      if (returnType == null) {
        returnType = ((PsiMethod)resolve).getReturnType();
      }
      PsiType methodReturnType = subst.substitute(returnType);
      if (interfaceReturnType != null && interfaceReturnType != PsiType.VOID) {
        if (methodReturnType == null) {
          methodReturnType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(((PsiMethod)resolve).getContainingClass(), subst);
        }
        if (!TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType, false)) {
          return "Bad return type in method reference: cannot convert " + methodReturnType.getCanonicalText() + " to " + interfaceReturnType.getCanonicalText();
        }
      }
    }
    return null;
  }

  public static boolean isCorrectAssignment(PsiType[] signatureParameterTypes2,
                                            PsiType[] parameterTypes,
                                            boolean varargs,
                                            int offset) {
    final int min = Math.min(signatureParameterTypes2.length, parameterTypes.length - offset);
    for (int i = 0; i < min; i++) {
      final PsiType type1 = parameterTypes[i + offset];
      final PsiType type2 = signatureParameterTypes2[i];
      if (varargs && i == signatureParameterTypes2.length - 1) {
        if (!TypeConversionUtil.isAssignable(type2, type1) && !TypeConversionUtil.isAssignable(((PsiArrayType)type2).getComponentType(), type1)) {
          return false;
        }
      }
      else if (!TypeConversionUtil.isAssignable(type2, type1)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public static Map<PsiMethodReferenceExpression, PsiType> getFunctionalTypeMap() {
    Map<PsiMethodReferenceExpression, PsiType> map = ourRefs.get();
    if (map == null) {
      map = new HashMap<PsiMethodReferenceExpression, PsiType>();
      ourRefs.set(map);
    }
    return map;
  }

  public static class QualifierResolveResult {
    private final PsiClass myContainingClass;
    private final PsiSubstitutor mySubstitutor;
    private final boolean myReferenceTypeQualified;

    public QualifierResolveResult(PsiClass containingClass, PsiSubstitutor substitutor, boolean referenceTypeQualified) {
      myContainingClass = containingClass;
      mySubstitutor = substitutor;
      myReferenceTypeQualified = referenceTypeQualified;
    }

    @Nullable
    public PsiClass getContainingClass() {
      return myContainingClass;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    public boolean isReferenceTypeQualified() {
      return myReferenceTypeQualified;
    }
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

  @NotNull
  public static QualifierResolveResult getQualifierResolveResult(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    PsiClass containingClass = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final PsiExpression expression = methodReferenceExpression.getQualifierExpression();
    if (expression != null) {
      final PsiType expressionType = getExpandedType(expression.getType(), expression);
      PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(expressionType);
      containingClass = result.getElement();
      if (containingClass != null) {
        substitutor = result.getSubstitutor();
      }
      if (containingClass == null && expression instanceof PsiReferenceExpression) {
        final JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
        final PsiElement resolve = resolveResult.getElement();
        if (resolve instanceof PsiClass) {
          containingClass = (PsiClass)resolve;
          substitutor = resolveResult.getSubstitutor();
          final boolean isRawSubst = !methodReferenceExpression.isConstructor() && 
                                     PsiTreeUtil.isAncestor(containingClass, methodReferenceExpression, true) && 
                                     PsiUtil.isRawSubstitutor(containingClass, substitutor);
          return new QualifierResolveResult(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : substitutor, true);
        }
      }
    }
    else {
      final PsiTypeElement typeElement = methodReferenceExpression.getQualifierType();
      if (typeElement != null) {
        PsiType type = getExpandedType(typeElement.getType(), typeElement);
        PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
        containingClass = result.getElement();
        if (containingClass != null) {
          substitutor = result.getSubstitutor();
        }
      }
    }
    return new QualifierResolveResult(containingClass, substitutor, false);
  }
  
  public static boolean isStaticallyReferenced(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
    if (qualifierExpression != null) {
      return qualifierExpression instanceof PsiReferenceExpression &&
             ((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiClass;
    }
    return true;
  }

  public static boolean isReceiverType(@Nullable PsiClass aClass, @Nullable PsiClass containingClass) {
    return InheritanceUtil.isInheritorOrSelf(aClass, containingClass, true);
  }

  public static boolean isReceiverType(PsiType receiverType, @Nullable PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    if (containingClass != null) {
      receiverType = getExpandedType(receiverType, containingClass);
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(receiverType);
    final PsiClass receiverClass = resolveResult.getElement();
    if (receiverClass != null && isReceiverType(receiverClass, containingClass)) {
      LOG.assertTrue(containingClass != null);
      return emptyOrRaw(containingClass, psiSubstitutor) ||
             TypeConversionUtil.isAssignable(JavaPsiFacade.getElementFactory(containingClass.getProject()).createType(containingClass, psiSubstitutor), receiverType);
    }
    return false;
  }

  private static boolean emptyOrRaw(PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    return PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor) ||
           psiSubstitutor.getSubstitutionMap().isEmpty();
  }

  public static boolean isReceiverType(PsiType functionalInterfaceType, PsiClass containingClass, @Nullable PsiMethod referencedMethod) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final MethodSignature function = LambdaUtil.getFunction(resolveResult.getElement());
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

  private static PsiType getExpandedType(PsiType type, @NotNull PsiElement typeElement) {
    if (type instanceof PsiArrayType) {
      type = JavaPsiFacade.getElementFactory(typeElement.getProject())
        .getArrayClassType(((PsiArrayType)type).getComponentType(), PsiUtil.getLanguageLevel(typeElement));
    }
    return type;
  }

  public static String checkMethodReferenceContext(PsiMethodReferenceExpression methodRef) {
    final PsiElement resolve = methodRef.resolve();

    if (resolve == null) return null;
    return checkMethodReferenceContext(methodRef, resolve, methodRef.getFunctionalInterfaceType());
  }

  public static String checkMethodReferenceContext(PsiMethodReferenceExpression methodRef,
                                                   PsiElement resolve,
                                                   PsiType functionalInterfaceType) {
    final PsiClass containingClass = resolve instanceof PsiMethod ? ((PsiMethod)resolve).getContainingClass() : (PsiClass)resolve;
    final boolean isStaticSelector = isStaticallyReferenced(methodRef);
    final PsiElement qualifier = methodRef.getQualifier();

    boolean isMethodStatic = false;
    boolean receiverReferenced = false;
    boolean isConstructor = true;

    if (resolve instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)resolve;

      isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
      isConstructor = method.isConstructor();
      receiverReferenced = hasReceiver(methodRef, method, functionalInterfaceType);
      
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && qualifier instanceof PsiSuperExpression) {
        return "Abstract method '" + method.getName() + "' cannot be accessed directly";
      }
    }

    if (!receiverReferenced && isStaticSelector && !isMethodStatic && !isConstructor) {
      return "Non-static method cannot be referenced from a static context";
    }

    if (!receiverReferenced && !isStaticSelector && isMethodStatic) {
      return "Static method referenced through non-static qualifier";
    }

    if (receiverReferenced && isStaticSelector && isMethodStatic && !isConstructor) {
      return "Static method referenced through receiver";
    }

    if (isMethodStatic && isStaticSelector && qualifier instanceof PsiTypeElement) {
      final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(qualifier, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          return "Parameterized qualifier on static method reference";
        }
      }
    }

    if (isConstructor) {
      if (containingClass != null && PsiUtil.isInnerClass(containingClass) && containingClass.isPhysical()) {
        PsiClass outerClass = containingClass.getContainingClass();
        if (outerClass != null && !InheritanceUtil.hasEnclosingInstanceInScope(outerClass, methodRef, true, false)) {
           return "An enclosing instance of type " + PsiFormatUtil.formatClass(outerClass, PsiFormatUtilBase.SHOW_NAME) + " is not in scope";
        }
      }
    }
    return null;
  }

  public static boolean hasReceiver(@NotNull PsiMethodReferenceExpression methodRef,
                                    @NotNull PsiMethod method) {
    return hasReceiver(methodRef, method, methodRef.getFunctionalInterfaceType());
  }

  private static boolean hasReceiver(@NotNull PsiMethodReferenceExpression methodRef,
                                     @NotNull PsiMethod method,
                                     PsiType functionalInterfaceType) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    final MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null;
    LOG.assertTrue(signature != null);
    final PsiType[] parameterTypes = signature.getParameterTypes();
    final QualifierResolveResult qualifierResolveResult = getQualifierResolveResult(methodRef);
    return (method.getParameterList().getParametersCount() + 1 == parameterTypes.length || method.isVarArgs() && parameterTypes.length > 0)&&
           hasReceiver(parameterTypes, qualifierResolveResult, methodRef);
  }

  public static String checkTypeArguments(PsiTypeElement qualifier, PsiType psiType) {
    if (psiType instanceof PsiClassType) {
      final PsiJavaCodeReferenceElement referenceElement = qualifier.getInnermostComponentReferenceElement();
      if (referenceElement != null) {
        PsiType[] typeParameters = referenceElement.getTypeParameters();
        for (PsiType typeParameter : typeParameters) {
          if (typeParameter instanceof PsiWildcardType) {
            return "Unexpected wildcard";
          }
        }
      }
    }
    return null;
  }
}
