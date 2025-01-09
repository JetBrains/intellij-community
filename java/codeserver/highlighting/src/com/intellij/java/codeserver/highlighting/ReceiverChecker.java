// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ReceiverChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  ReceiverChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkReceiver(@NotNull PsiReceiverParameter parameter) {
    if (!myVisitor.hasErrorResults()) myVisitor.checkFeature(parameter, JavaFeature.RECEIVERS);
    if (!myVisitor.hasErrorResults()) checkReceiverPlacement(parameter);
    if (!myVisitor.hasErrorResults()) checkReceiverType(parameter);
  }

  private void checkReceiverType(@NotNull PsiReceiverParameter parameter) {
    PsiElement owner = parameter.getParent().getParent();
    if (!(owner instanceof PsiMethod method)) return;

    PsiClass enclosingClass = method.getContainingClass();
    boolean isConstructor = method.isConstructor();
    if (isConstructor && enclosingClass != null) {
      enclosingClass = enclosingClass.getContainingClass();
    }

    if (enclosingClass == null) return;
    PsiClassType type = PsiElementFactory.getInstance(parameter.getProject()).createType(enclosingClass, PsiSubstitutor.EMPTY);
    if (!type.equals(parameter.getType())) {
      myVisitor.report(JavaErrorKinds.RECEIVER_TYPE_MISMATCH.create(parameter, type));
      return;
    }

    PsiThisExpression identifier = parameter.getIdentifier();
    if (!enclosingClass.equals(PsiUtil.resolveClassInType(identifier.getType()))) {
      String name;
      if (isConstructor) {
        String className = enclosingClass.getName();
        name = className != null ? className + ".this" : null;
      }
      else {
        name = "this";
      }
      myVisitor.report(JavaErrorKinds.RECEIVER_NAME_MISMATCH.create(parameter, name));
    }
  }

  private void checkReceiverPlacement(@NotNull PsiReceiverParameter parameter) {
    PsiElement owner = parameter.getParent().getParent();
    if (owner == null) return;

    if (!(owner instanceof PsiMethod method)) {
      myVisitor.report(JavaErrorKinds.RECEIVER_WRONG_CONTEXT.create(parameter));
      return;
    }

    if (isStatic(method) || method.isConstructor() && isStatic(method.getContainingClass())) {
      myVisitor.report(JavaErrorKinds.RECEIVER_STATIC_CONTEXT.create(parameter));
      return;
    }

    if (!PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(parameter), JavaTokenType.LPARENTH)) {
      myVisitor.report(JavaErrorKinds.RECEIVER_WRONG_POSITION.create(parameter));
    }
  }
  private static boolean isStatic(@Nullable PsiModifierListOwner owner) {
    if (owner == null) return false;
    if (owner instanceof PsiClass psiClass && ClassUtil.isTopLevelClass(psiClass)) return true;
    PsiModifierList modifierList = owner.getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC);
  }
}
