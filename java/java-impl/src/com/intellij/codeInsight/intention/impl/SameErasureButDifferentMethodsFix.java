// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// @Override void f(List<String> p);   ->      @Override void f(List<? super String> p);
public class SameErasureButDifferentMethodsFix extends PsiUpdateModCommandAction<PsiMethod> {
  private final SmartPsiElementPointer<PsiMethod> superMethodPtr;

  public SameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    super(method);
    superMethodPtr = SmartPointerManager.createPointer(superMethod);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod method, @NotNull ModPsiUpdater updater) {
    PsiMethod superMethod = superMethodPtr.getElement();
    if (superMethod == null || !superMethod.isValid()) return;
    PsiClass containingClass = method.getContainingClass();
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (containingClass == null || superContainingClass == null) return;
    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance()
      .getSuperClassSubstitutor(superContainingClass, containingClass, containingClass.getResolveScope(), PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
    if (parameters.length != superParameters.length) return;
    updater.trackDeclaration(method);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiParameter superParameter = superParameters[i];
      PsiType superParameterType = superSubstitutor.substitute(superParameter.getType());

      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement != null && !superParameterType.equals(typeElement.getType())) {
        typeElement.replace(factory.createTypeElement(superParameterType));
      }
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod method) {
    PsiMethod superMethod = superMethodPtr.getElement();
    if (superMethod == null || !superMethod.isValid()) return null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.project());
    PsiClass containingClass = method.getContainingClass();
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (containingClass == null || superContainingClass == null) return null;
    if (!facade.getResolveHelper().isAccessible(superMethod, containingClass, null)) return null;

    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance()
      .getSuperClassSubstitutor(superContainingClass, containingClass, containingClass.getResolveScope(), PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return null;
    MethodSignature superSignature = superMethod.getSignature(superSubstitutor);
    if (method.getParameterList().getParametersCount() != superMethod.getParameterList().getParametersCount()) return null;
    if (signature.equals(superSignature) || !MethodSignatureUtil.areSignaturesErasureEqual(signature, superSignature)) return null;
    return Presentation.of(JavaBundle.message("intention.text.fix.method.0.parameters.with.bounded.wildcards", method.getName()));
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.fix.bounded.wildcards");
  }
}
