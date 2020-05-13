/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

public class PsiMethodReferenceHighlightingUtil {
  static HighlightInfo checkRawConstructorReference(@NotNull PsiMethodReferenceExpression expression) {
    if (expression.isConstructor()) {
      PsiType[] typeParameters = expression.getTypeParameters();
      if (typeParameters.length > 0) {
        PsiElement qualifier = expression.getQualifier();
        if (qualifier instanceof PsiReferenceExpression) {
          PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
          if (resolve instanceof PsiClass && ((PsiClass)resolve).hasTypeParameters()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
              .descriptionAndTooltip("Raw constructor reference with explicit type parameters for constructor").create();
          }
        }
      }
    }
    return null;
  }

  static String checkMethodReferenceContext(@NotNull PsiMethodReferenceExpression methodRef) {
    final PsiElement resolve = methodRef.resolve();

    if (resolve == null) return null;
    return checkMethodReferenceContext(methodRef, resolve, methodRef.getFunctionalInterfaceType());
  }

  public static String checkMethodReferenceContext(@NotNull PsiMethodReferenceExpression methodRef,
                                                   @NotNull PsiElement resolve,
                                                   PsiType functionalInterfaceType) {
    final PsiClass containingClass = resolve instanceof PsiMethod ? ((PsiMethod)resolve).getContainingClass() : (PsiClass)resolve;
    final boolean isStaticSelector = PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
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
      receiverReferenced = PsiMethodReferenceUtil.isResolvedBySecondSearch(methodRef,
                                                    interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null,
                                                                           method.isVarArgs(),
                                                                           isMethodStatic,
                                                                           method.getParameterList().getParametersCount());

      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && qualifier instanceof PsiSuperExpression) {
        return JavaErrorBundle.message("abstract.method.0.cannot.be.accessed.directly.method.reference.context", method.getName());
      }
    }

    if (!receiverReferenced) {
      if (isStaticSelector && !isMethodStatic && !isConstructor) {
        return JavaErrorBundle.message("non.static.method.cannot.be.referenced.from.a.static.context.method.reference.context");
      }
      if (!isStaticSelector && isMethodStatic) {
        return JavaErrorBundle.message("static.method.referenced.through.non.static.qualifier.method.reference.context");
      }
    }
    else if (isStaticSelector && isMethodStatic) {
      return JavaErrorBundle.message("static.method.referenced.through.receiver.method.reference.context");
    }

    if (isMethodStatic && isStaticSelector && qualifier instanceof PsiTypeElement) {
      final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(qualifier, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          return JavaErrorBundle.message("parameterized.qualifier.on.static.method.reference.context");
        }
      }
    }

    if (isConstructor) {
      if (containingClass != null && PsiUtil.isInnerClass(containingClass) && containingClass.isPhysical()) {
        PsiClass outerClass = containingClass.getContainingClass();
        if (outerClass != null && !InheritanceUtil.hasEnclosingInstanceInScope(outerClass, methodRef, true, false)) {
           return JavaErrorBundle.message("an.enclosing.instance.of.type.not.in.scope.method.reference.context",
                                        PsiFormatUtil.formatClass(outerClass, PsiFormatUtilBase.SHOW_NAME));
        }
      }
    }
    return null;
  }
}
