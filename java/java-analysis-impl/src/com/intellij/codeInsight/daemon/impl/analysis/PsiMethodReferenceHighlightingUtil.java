// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

public final class PsiMethodReferenceHighlightingUtil {
  static HighlightInfo checkRawConstructorReference(@NotNull PsiMethodReferenceExpression expression) {
    if (expression.isConstructor()) {
      PsiType[] typeParameters = expression.getTypeParameters();
      if (typeParameters.length > 0) {
        PsiElement qualifier = expression.getQualifier();
        if (qualifier instanceof PsiReferenceExpression) {
          PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
          if (resolve instanceof PsiClass && ((PsiClass)resolve).hasTypeParameters()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
              .descriptionAndTooltip(JavaAnalysisBundle.message("text.raw.ctor.reference.with.type.parameters")).create();
          }
        }
      }
    }
    return null;
  }

  static @NlsContexts.DetailedDescription String checkMethodReferenceContext(@NotNull PsiMethodReferenceExpression methodRef) {
    PsiElement resolve = methodRef.resolve();

    if (resolve == null) return null;
    return checkMethodReferenceContext(methodRef, resolve, methodRef.getFunctionalInterfaceType());
  }

  public static @NlsContexts.DetailedDescription String checkMethodReferenceContext(@NotNull PsiMethodReferenceExpression methodRef,
                                                                                    @NotNull PsiElement resolve,
                                                                                    PsiType functionalInterfaceType) {
    PsiClass containingClass = resolve instanceof PsiMethod ? ((PsiMethod)resolve).getContainingClass() : (PsiClass)resolve;
    boolean isStaticSelector = PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
    PsiElement qualifier = methodRef.getQualifier();

    boolean isMethodStatic = false;
    boolean receiverReferenced = false;
    boolean isConstructor = true;

    if (resolve instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)resolve;

      isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
      isConstructor = method.isConstructor();

      PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
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
      PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(qualifier, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        PsiReferenceParameterList parameterList = referenceElement.getParameterList();
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
