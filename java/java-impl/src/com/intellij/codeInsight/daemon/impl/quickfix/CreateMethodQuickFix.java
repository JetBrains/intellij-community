// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CreateMethodQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String mySignature;
  private final String myBody;

  private CreateMethodQuickFix(final PsiClass targetClass, final @NonNls String signature, final @NonNls String body) {
    super(targetClass);
    mySignature = signature;
    myBody = body;
  }

  @Override
  public @NotNull String getText() {
    PsiClass myTargetClass = (PsiClass)getStartElement();
    String signature = myTargetClass == null ? "" :
                       PsiFormatUtil.formatMethod(createMethod(myTargetClass), PsiSubstitutor.EMPTY,
                                                  PsiFormatUtilBase.SHOW_NAME |
                                                  PsiFormatUtilBase.SHOW_TYPE |
                                                  PsiFormatUtilBase.SHOW_PARAMETERS |
                                                  PsiFormatUtilBase.SHOW_RAW_TYPE,
                                                  PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_RAW_TYPE, 2);
    return QuickFixBundle.message("create.method.from.usage.text", signature);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass myTargetClass = (PsiClass)startElement;

    PsiMethod method = createMethod(myTargetClass);
    List<Pair<PsiExpression, PsiType>> arguments =
      ContainerUtil.map(method.getParameterList().getParameters(), psiParameter -> Pair.create(null, psiParameter.getType()));

    method = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(myTargetClass.add(method));
    CreateMethodFromUsageFix.doCreate(myTargetClass, method, arguments, PsiSubstitutor.EMPTY, ExpectedTypeInfo.EMPTY_ARRAY, method);
  }

  private PsiMethod createMethod(@NotNull PsiClass myTargetClass) {
    Project project = myTargetClass.getProject();
    JVMElementFactory elementFactory = JVMElementFactories.getFactory(myTargetClass.getLanguage(), project);
    if (elementFactory == null) {
      elementFactory = JavaPsiFacade.getElementFactory(project);
    }
    String methodText = mySignature + (myTargetClass.isInterface() ? ";" : "{" + myBody + "}");
    return elementFactory.createMethodFromText(methodText, null);
  }

  public static @Nullable CreateMethodQuickFix createFix(@NotNull PsiClass targetClass, final @NonNls String signature, final @NonNls String body) {
    CreateMethodQuickFix fix = new CreateMethodQuickFix(targetClass, signature, body);
    try {
      fix.createMethod(targetClass);
      return fix;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }
}
