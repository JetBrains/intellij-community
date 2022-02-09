// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// @Override void f(List<String> p);   ->      @Override void f(List<? super String> p);
public class SameErasureButDifferentMethodsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final SmartPsiElementPointer<PsiMethod> methodPtr;

  public SameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    super(superMethod);
    methodPtr = SmartPointerManager.getInstance(method.getProject()).createSmartPsiElementPointer(method);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!isAvailable(project, file, startElement, endElement)) return;
    PsiMethod superMethod = (PsiMethod)startElement;
    PsiMethod method = methodPtr.getElement();
    if (method == null || !method.isValid()) return;
    PsiClass containingClass = method.getContainingClass();
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (containingClass == null || superContainingClass == null) return;
    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance()
      .getSuperClassSubstitutor(superContainingClass, containingClass, containingClass.getResolveScope(), PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
    if (parameters.length != superParameters.length) return;
    ParameterInfoImpl[] infos = new ParameterInfoImpl[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiParameter superParameter = superParameters[i];
      PsiType superParameterType = superSubstitutor.substitute(superParameter.getType());
      infos[i] = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(superParameterType);
    }

    var provider = JavaSpecialRefactoringProvider.getInstance();
    var processor = provider.getChangeSignatureProcessorWithCallback(project, method, false, null, method.getName(), method.getReturnType(), infos, true, null);

    processor.run();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiMethod superMethod = (PsiMethod)startElement;
    PsiMethod method = methodPtr.getElement();
    if (method == null || !method.isValid()) return false;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass containingClass = method.getContainingClass();
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (containingClass == null || superContainingClass == null) return false;
    if (!facade.getResolveHelper().isAccessible(superMethod, containingClass, null)) return false;

    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance()
      .getSuperClassSubstitutor(superContainingClass, containingClass, containingClass.getResolveScope(), PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return false;
    MethodSignature superSignature = superMethod.getSignature(superSubstitutor);
    if (method.getParameterList().getParametersCount() != superMethod.getParameterList().getParametersCount()) return false;
    return !signature.equals(superSignature) && MethodSignatureUtil.areSignaturesErasureEqual(signature, superSignature);
  }

  @NotNull
  @Override
  public String getText() {
    PsiMethod method = methodPtr.getElement();
    if (method == null || !method.isValid()) return getFamilyName();
    return JavaBundle.message("intention.text.fix.method.0.parameters.with.bounded.wildcards", method.getName());
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.fix.bounded.wildcards");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
