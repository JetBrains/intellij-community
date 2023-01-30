// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaClassSupers;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

// @Override void f(List<String> p);   ->      @Override void f(List<? super String> p);
public class SameErasureButDifferentMethodsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final SmartPsiElementPointer<PsiMethod> methodPtr;

  public SameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    super(superMethod);
    methodPtr = SmartPointerManager.getInstance(method.getProject()).createSmartPsiElementPointer(method);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiMethod method = methodPtr.getElement();
    if (method == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    if (!(getStartElement() instanceof PsiMethod superMethod)) {
      return IntentionPreviewInfo.EMPTY;
    }
    List<ParameterInfoImpl> infos = getParameterInfos(superMethod, method);
    String before = getMethodDescription(method, null);
    String after = getMethodDescription(method, infos);
    if (before == null || after == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, before, after);
  }

  @Nullable
  private static String getMethodDescription(@Nullable PsiMethod method, @Nullable List<ParameterInfoImpl> infos) {
    if (method == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    PsiCodeBlock body = method.getBody();
    PsiParameterList list = method.getParameterList();
    for (PsiElement child : method.getChildren()) {
      if (child == body) {
        break;
      }
      if (child == list && infos != null) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (ParameterInfoImpl info : infos) {
          joiner.add(info.getTypeText() + " " + info.getName());
        }
        builder.append(joiner);
        continue;
      }
      builder.append(child.getText());
    }
    return builder.toString();
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
    List<ParameterInfoImpl> infos = getParameterInfos(superMethod, method);
    if (infos == null) return;

    var processor = JavaRefactoringFactory.getInstance(project)
      .createChangeSignatureProcessor(method, false, null, method.getName(), method.getReturnType(), infos.toArray(new ParameterInfoImpl[]{}),
                                      null, null, null, null);

    processor.run();
  }

  @Nullable
  private static List<ParameterInfoImpl> getParameterInfos(PsiMethod superMethod, PsiMethod method) {
    if (method == null || !method.isValid()) return null;
    PsiClass containingClass = method.getContainingClass();
    PsiClass superContainingClass = superMethod.getContainingClass();
    if (containingClass == null || superContainingClass == null) return null;
    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance()
      .getSuperClassSubstitutor(superContainingClass, containingClass, containingClass.getResolveScope(), PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return null;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
    if (parameters.length != superParameters.length) return null;
    List<ParameterInfoImpl> infos = new ArrayList<>(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiParameter superParameter = superParameters[i];
      PsiType superParameterType = superSubstitutor.substitute(superParameter.getType());
      infos.add(ParameterInfoImpl.create(i).withName(parameter.getName()).withType(superParameterType));
    }
    return infos;
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
