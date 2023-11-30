// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
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

public class AddMethodFix extends PsiUpdateModCommandAction<PsiClass> {
  private final SmartPsiElementPointer<PsiMethod> myMethodPrototype;
  private final List<String> myExceptions = new ArrayList<>();

  public AddMethodFix(@NotNull PsiMethod methodPrototype, @NotNull PsiClass implClass) {
    super(implClass);
    myMethodPrototype = SmartPointerManager.createPointer(methodPrototype);
  }

  public AddMethodFix(@NonNls @NotNull String methodText, @NotNull PsiClass implClass, String @NotNull ... exceptions) {
    this(createMethod(methodText, implClass), implClass);
    ContainerUtil.addAll(myExceptions, exceptions);
  }

  @NotNull
  private static PsiMethod createMethod(final String methodText, final PsiClass implClass) {
    return JavaPsiFacade.getElementFactory(implClass.getProject()).createMethodFromText(methodText, implClass);
  }

  private static PsiMethod reformat(Project project, PsiMethod result) throws IncorrectOperationException {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    result = (PsiMethod)codeStyleManager.reformat(result);

    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    result = (PsiMethod)javaCodeStyleManager.shortenClassReferences(result);
    return result;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.method.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass myClass) {
    PsiMethod methodPrototype = myMethodPrototype.getElement();

    if (methodPrototype == null || !methodPrototype.isValid() ||
        MethodSignatureUtil.findMethodBySignature(myClass, methodPrototype, false) != null) {
      return null;
    }
    return Presentation.of(QuickFixBundle.message("add.method.text", methodPrototype.getName(), myClass.getName()));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass psiClass, @NotNull ModPsiUpdater updater) {
    PsiMethod method = createMethod(psiClass);
    if (method.getContainingFile().getOriginalFile() == context.file()) {
      GenerateMembersUtil.positionCaret(updater, method, true);
    }
  }
  
  public PsiMethod createMethod(@NotNull PsiClass psiClass) {
    PsiMethod methodPrototype = myMethodPrototype.getElement();
    if (methodPrototype == null) return null;

    PsiCodeBlock body;
    if (psiClass.isInterface() && (body = methodPrototype.getBody()) != null) body.delete();
    for (String exception : myExceptions) {
      PsiUtil.addException(methodPrototype, exception);
    }
    PsiMethod method = (PsiMethod)psiClass.add(methodPrototype);
    return (PsiMethod)method.replace(reformat(psiClass.getProject(), method));
  }
}
