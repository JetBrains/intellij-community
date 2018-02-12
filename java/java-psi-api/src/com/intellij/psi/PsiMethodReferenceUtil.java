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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiMethodReferenceUtil {
  private static final Logger LOG = Logger.getInstance(PsiMethodReferenceUtil.class);

  public static boolean isSecondSearchPossible(PsiType[] parameterTypes,
                                               QualifierResolveResult qualifierResolveResult,
                                               PsiMethodReferenceExpression methodRef) {
    if (parameterTypes.length > 0 &&
        !(parameterTypes[0] instanceof PsiPrimitiveType) &&
        !methodRef.isConstructor() &&
        isStaticallyReferenced(methodRef) &&
        isReceiverType(parameterTypes[0], qualifierResolveResult.getContainingClass(), qualifierResolveResult.getSubstitutor())) {
      return true;
    }
    return false;
  }

  public static boolean isResolvedBySecondSearch(@NotNull PsiMethodReferenceExpression methodRef,
                                                 @Nullable MethodSignature signature,
                                                 boolean varArgs,
                                                 boolean isStatic,
                                                 int parametersCount) {
    if (signature == null) {
      return false;
    }
    final QualifierResolveResult qualifierResolveResult = getQualifierResolveResult(methodRef);
    final PsiType[] functionalMethodParameterTypes = signature.getParameterTypes();
    return (parametersCount + 1 == functionalMethodParameterTypes.length && !varArgs ||
            varArgs && functionalMethodParameterTypes.length > 0 && !isStatic) &&
           isSecondSearchPossible(functionalMethodParameterTypes, qualifierResolveResult, methodRef);
  }

  @Nullable
  public static PsiType getQualifierType(PsiMethodReferenceExpression expression) {
    final PsiTypeElement typeElement = expression.getQualifierType();
    if (typeElement != null) {
      return typeElement.getType();
    } else {
      PsiType qualifierType = null;
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiExpression) {
        qualifierType = ((PsiExpression)qualifier).getType();
      }
      if (qualifierType == null && qualifier instanceof PsiReferenceExpression) {
        return JavaPsiFacade.getElementFactory(expression.getProject()).createType((PsiReferenceExpression)qualifier);
      }
      return qualifierType;
    }
  }

  public static boolean isReturnTypeCompatible(PsiMethodReferenceExpression expression,
                                               JavaResolveResult result,
                                               PsiType functionalInterfaceType) {
    return isReturnTypeCompatible(expression, result, functionalInterfaceType, null);
  }

  /**
   * Returns actual return type of method reference (not the expected one)
   *
   * @param expression a method reference to get the return type of
   * @return an actual method reference return type
   */
  public static PsiType getMethodReferenceReturnType(PsiMethodReferenceExpression expression) {
    return getMethodReferenceReturnType(expression, expression.advancedResolve(false));
  }

  /**
   * Returns actual return type of method reference (not the expected one)
   *
   * @param expression a method reference to get the return type of
   * @param result the result of method reference resolution
   * @return an actual method reference return type
   */
  private static PsiType getMethodReferenceReturnType(PsiMethodReferenceExpression expression, JavaResolveResult result) {
    PsiSubstitutor subst = result.getSubstitutor();

    PsiType methodReturnType = null;
    PsiClass containingClass = null;
    final PsiElement resolve = result.getElement();
    if (resolve instanceof PsiMethod) {
      containingClass = ((PsiMethod)resolve).getContainingClass();
      methodReturnType = PsiTypesUtil.patchMethodGetClassReturnType(expression, (PsiMethod)resolve);
      if (methodReturnType == null) {
        methodReturnType = ((PsiMethod)resolve).getReturnType();
        if (PsiType.VOID.equals(methodReturnType)) {
          return methodReturnType;
        }

        methodReturnType = subst.substitute(methodReturnType);
      }
    }
    else if (resolve instanceof PsiClass) {
      if (PsiEquivalenceUtil.areElementsEquivalent(resolve, JavaPsiFacade.getElementFactory(expression.getProject()).getArrayClass(PsiUtil.getLanguageLevel(expression)))) {
        final PsiTypeParameter[] typeParameters = ((PsiClass)resolve).getTypeParameters();
        if (typeParameters.length == 1) {
          final PsiType arrayComponentType = subst.substitute(typeParameters[0]);
          if (arrayComponentType == null) {
            return null;
          }
          methodReturnType = arrayComponentType.createArrayType();
        }
      }
      containingClass = (PsiClass)resolve;
    }

    if (methodReturnType == null) {
      if (containingClass == null) {
        return null;
      }
      methodReturnType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(containingClass, subst);
    }

    return PsiUtil.captureToplevelWildcards(methodReturnType, expression);
  }

  private static boolean isReturnTypeCompatible(PsiMethodReferenceExpression expression,
                                                JavaResolveResult result,
                                                PsiType functionalInterfaceType,
                                                Ref<String> errorMessage) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod != null) {
      final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);

      if (PsiType.VOID.equals(interfaceReturnType) || interfaceReturnType == null) {
        return true;
      }

      PsiType methodReturnType = getMethodReferenceReturnType(expression, result);
      if (methodReturnType == null || PsiType.VOID.equals(methodReturnType)) {
        return false;
      }

      if (TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType)) {
        return true;
      }
     
      if (errorMessage != null) {
        errorMessage.set("Bad return type in method reference: " +
                         "cannot convert " + methodReturnType.getCanonicalText() + " to " + interfaceReturnType.getCanonicalText());
      }
    }
    return false;
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
      PsiType expressionType = expression.getType();
      if (expressionType instanceof PsiCapturedWildcardType) {
        expressionType = ((PsiCapturedWildcardType)expressionType).getUpperBound();
      }
      else {
        expressionType = replaceArrayType(expressionType, expression);
      }
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
          return new QualifierResolveResult(containingClass, substitutor, true);
        }
      }
    }
    else {
      final PsiTypeElement typeElement = methodReferenceExpression.getQualifierType();
      if (typeElement != null) {
        PsiType type = replaceArrayType(typeElement.getType(), typeElement);
        PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
        containingClass = result.getElement();
        if (containingClass != null) {
          return new QualifierResolveResult(containingClass, result.getSubstitutor(), true);
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

  //if P1, ..., Pn is not empty and P1 is a subtype of ReferenceType, then the method reference expression is treated as 
  // if it were a method invocation expression with argument expressions of types P2, ...,Pn.
  public static boolean isReceiverType(@Nullable PsiType receiverType, PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    if (receiverType == null) {
      return false;
    }
    return TypeConversionUtil.isAssignable(JavaPsiFacade.getElementFactory(containingClass.getProject()).createType(containingClass, psiSubstitutor), 
                                           replaceArrayType(receiverType, containingClass));
  }

  public static PsiType getFirstParameterType(PsiType functionalInterfaceType, PsiElement context) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final MethodSignature function = LambdaUtil.getFunction(resolveResult.getElement());
    if (function != null) {
      final int interfaceMethodParamsLength = function.getParameterTypes().length;
      if (interfaceMethodParamsLength > 0) {
        PsiType type = resolveResult.getSubstitutor().substitute(function.getParameterTypes()[0]);
        return type != null ? PsiUtil.captureToplevelWildcards(type, context) : null;
      }
    }
    return null;
  }

  private static PsiType replaceArrayType(PsiType type, @NotNull PsiElement context) {
    if (type instanceof PsiArrayType) {
      type = JavaPsiFacade.getElementFactory(context.getProject())
        .getArrayClassType(((PsiArrayType)type).getComponentType(), PsiUtil.getLanguageLevel(context));
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

      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      receiverReferenced = isResolvedBySecondSearch(methodRef,
                                                    interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null, 
                                                    method.isVarArgs(), 
                                                    isMethodStatic,
                                                    method.getParameterList().getParametersCount());

      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && qualifier instanceof PsiSuperExpression) {
        return "Abstract method '" + method.getName() + "' cannot be accessed directly";
      }
    }

    if (!receiverReferenced) {
      if (isStaticSelector && !isMethodStatic && !isConstructor) {
        return "Non-static method cannot be referenced from a static context";
      }
      if (!isStaticSelector && isMethodStatic) {
        return "Static method referenced through non-static qualifier";
      }
    }
    else if (isStaticSelector && isMethodStatic) {
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

  public static String checkReturnType(PsiMethodReferenceExpression expression, JavaResolveResult result, PsiType functionalInterfaceType) {
    final Ref<String> errorMessage = Ref.create();
    if (!isReturnTypeCompatible(expression, result, functionalInterfaceType, errorMessage)) {
      return errorMessage.get();
    }
    return null;
  }
}
