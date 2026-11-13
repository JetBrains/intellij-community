// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CreateMethodFromUsageFix {
  public static boolean isMethodSignatureExists(PsiMethodCallExpression call, PsiClass target) {
    String name = call.getMethodExpression().getReferenceName();
    final JavaResolveResult resolveResult = call.getMethodExpression().advancedResolve(false);
    PsiExpressionList list = call.getArgumentList();
    PsiMethod[] methods = target.findMethodsByName(name, false);
    for (PsiMethod method : methods) {
      if (PsiUtil.isApplicable(method, resolveResult.getSubstitutor(), list)) return true;
    }
    return false;
  }

  public static boolean hasVoidInArgumentList(final PsiMethodCallExpression call) {
    PsiExpressionList argumentList = call.getArgumentList();
    for (PsiExpression expression : argumentList.getExpressions()) {
      PsiType type = expression.getType();
      if (type == null || PsiTypes.voidType().equals(type)) return true;
    }
    return false;
  }

  /**
   * Creates a new void no-arg method, adds it into class and returns it
   * 
   * @param targetClass class to add method to
   * @param parentClass context class from where creation was invoked
   * @param enclosingContext context from where creation was invoked
   * @param methodName name of the new method
   * @return newly created method
   */
  public static PsiMethod createMethod(@NotNull PsiClass targetClass,
                                       @Nullable PsiClass parentClass,
                                       @Nullable PsiMember enclosingContext,
                                       @NotNull String methodName) {
    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), targetClass.getProject());
    if (factory == null) {
      return null;
    }

    PsiMethod method = factory.createMethod(methodName, PsiTypes.voidType());

    return addMethod(targetClass, parentClass, enclosingContext, method);
  }

  public static @NotNull PsiMethod addMethod(@NotNull PsiClass targetClass, @Nullable PsiClass parentClass,
                                    @Nullable PsiMember enclosingContext, @NotNull PsiMethod method) {
    if (targetClass.equals(parentClass)) {
      method = (PsiMethod)targetClass.addAfter(method, enclosingContext);
    }
    else {
      PsiElement anchor = enclosingContext;
      while (anchor != null && anchor.getParent() != null && !anchor.getParent().equals(targetClass)) {
        anchor = anchor.getParent();
      }
      if (anchor != null && anchor.getParent() == null) anchor = null;
      if (anchor != null) {
        method = (PsiMethod)targetClass.addAfter(method, anchor);
      }
      else {
        method = (PsiMethod)targetClass.add(method);
      }
    }
    return method;
  }

  public static boolean checkTypeParam(final PsiMethod method, final PsiTypeParameter typeParameter) {
    final String typeParameterName = typeParameter.getName();

    final PsiTypeVisitor<Boolean> visitor = new PsiTypeVisitor<>() {
      @Override
      public Boolean visitClassType(@NotNull PsiClassType classType) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass instanceof PsiTypeParameter parameter &&
            PsiTreeUtil.isAncestor(parameter.getOwner(), method, true)) {
          return false;
        }
        if (Comparing.strEqual(typeParameterName, classType.getCanonicalText())) {
          return true;
        }
        for (PsiType p : classType.getParameters()) {
          if (p.accept(this)) return true;
        }
        return false;
      }

      @Override
      public Boolean visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
        return false;
      }

      @Override
      public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Override
      public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        if (bound != null) {
          return bound.accept(this);
        }
        return false;
      }
    };

    final PsiTypeElement rElement = method.getReturnTypeElement();
    if (rElement != null) {
      if (rElement.getType().accept(visitor)) return true;
    }


    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiTypeElement element = parameter.getTypeElement();
      if (element != null) {
        if (element.getType().accept(visitor)) return true;
      }
    }
    return false;
  }
}
