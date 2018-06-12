// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AddMethodFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final SmartPsiElementPointer<PsiMethod> myMethodPrototype;
  private final List<String> myExceptions = new ArrayList<>();
  private String myText;

  public AddMethodFix(@NotNull PsiMethod methodPrototype, @NotNull PsiClass implClass) {
    super(implClass);
    myMethodPrototype = SmartPointerManager.createPointer(methodPrototype);
    setText(QuickFixBundle.message("add.method.text", methodPrototype.getName(), implClass.getName()));
  }

  public AddMethodFix(@NonNls @NotNull String methodText, @NotNull PsiClass implClass, @NotNull String... exceptions) {
    this(createMethod(methodText, implClass), implClass);
    ContainerUtil.addAll(myExceptions, exceptions);
  }

  @NotNull
  private static PsiMethod createMethod(final String methodText, final PsiClass implClass) {
    return JavaPsiFacade.getInstance(implClass.getProject()).getElementFactory().createMethodFromText(methodText, implClass);
  }

  private static PsiMethod reformat(Project project, PsiMethod result) throws IncorrectOperationException {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    result = (PsiMethod)codeStyleManager.reformat(result);

    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    result = (PsiMethod)javaCodeStyleManager.shortenClassReferences(result);
    return result;
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  protected void setText(@NotNull String text) {
    myText = text;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.method.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;

    PsiMethod methodPrototype = myMethodPrototype.getElement();

    return methodPrototype != null &&
           methodPrototype.isValid() &&
           myClass.getManager().isInProject(myClass) &&
           myText != null &&
           MethodSignatureUtil.findMethodBySignature(myClass, methodPrototype, false) == null
      ;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiMethod methodPrototype = myMethodPrototype.getElement();
    if (methodPrototype == null) return;

    PsiClass myClass = (PsiClass)startElement;

    PsiCodeBlock body;
    if (myClass.isInterface() && (body = methodPrototype.getBody()) != null) body.delete();
    for (String exception : myExceptions) {
      PsiUtil.addException(methodPrototype, exception);
    }
    PsiMethod method = (PsiMethod)myClass.add(methodPrototype);
    method = (PsiMethod)method.replace(reformat(project, method));
    postAddAction(file, editor, method);
  }

  protected void postAddAction(@NotNull PsiFile file,
                               @Nullable("is null when called from inspection") Editor editor,
                               PsiMethod newMethod) {
    if (editor != null && newMethod.getContainingFile() == file) {
      GenerateMembersUtil.positionCaret(editor, newMethod, true);
    }
  }
}
