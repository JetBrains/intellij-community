// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CreateMethodQuickFix extends PsiUpdateModCommandAction<PsiClass> {
  private final String mySignature;
  private final String myBody;
  private final SmartPsiElementPointer<PsiClass> myTargetClass;

  private CreateMethodQuickFix(final PsiClass targetClass, final @NonNls String signature, final @NonNls String body) {
    super(targetClass);
    mySignature = signature;
    myBody = body;
    myTargetClass = SmartPointerManager.createPointer(targetClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass targetClass) {
    String signature = PsiFormatUtil.formatMethod(createMethod(targetClass), PsiSubstitutor.EMPTY,
                                                  PsiFormatUtilBase.SHOW_NAME |
                                                  PsiFormatUtilBase.SHOW_TYPE |
                                                  PsiFormatUtilBase.SHOW_PARAMETERS |
                                                  PsiFormatUtilBase.SHOW_RAW_TYPE,
                                                  PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_RAW_TYPE, 2);
    return Presentation.of(QuickFixBundle.message("create.method.from.usage.text", signature));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass targetClass, @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiMethod prototype = createMethod(targetClass);
    List<Pair<PsiExpression, PsiType>> arguments =
      ContainerUtil.map(prototype.getParameterList().getParameters(), psiParameter -> Pair.create(null, psiParameter.getType()));
    PsiMethod method = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(targetClass.add(prototype));

    RecordingTemplateBuilder fields = new RecordingTemplateBuilder();
    CreateFromUsageUtils.setupMethodParameters(method, fields, method, PsiSubstitutor.EMPTY, arguments);
    PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement != null) {
      new GuessTypeParameters(project, JavaPsiFacade.getElementFactory(project), fields, PsiSubstitutor.EMPTY)
        .setupTypeElement(returnTypeElement, ExpectedTypeInfo.EMPTY_ARRAY, method, targetClass);
    }
    boolean bodyFromTemplate = method.getBody() != null && StringUtil.isEmptyOrSpaces(myBody);
    if (bodyFromTemplate) {
      CreateFromUsageUtils.setupMethodBody(method, updater);
    }

    SmartPsiElementPointer<PsiMethod> methodPointer = SmartPointerManager.createPointer(method);
    updater.moveCaretTo(method);
    ModTemplateBuilder builder = updater.templateBuilder();
    fields.flushTo(builder);
    PsiDocumentManager.getInstance(project).commitDocument(updater.getDocument());
    PsiMethod liveMethod = methodPointer.getElement();
    if (liveMethod == null) return;

    PsiCodeBlock body = liveMethod.getBody();
    if (body == null) {
      updater.moveCaretTo(liveMethod.getTextRange().getEndOffset());
    }
    else {
      CreateFromUsageUtils.setupEditor(body, updater);
    }
    if (fields.isEmpty()) return;
    builder.finishAt(updater.getCaretOffset());
    if (bodyFromTemplate) {
      String methodName = liveMethod.getName();
      int parametersCount = arguments.size();
      builder.onTemplateFinished(_ -> correctBody(methodName, parametersCount));
    }
  }

  private @NotNull ModCommand correctBody(@NotNull String methodName, int parametersCount) {
    PsiClass targetClass = myTargetClass.getElement();
    if (targetClass == null) return ModCommand.nop();
    List<PsiMethod> methods = ContainerUtil.filter(
      targetClass.findMethodsByName(methodName, false),
      method -> method.getParameterList().getParametersCount() == parametersCount && method.getBody() != null);
    if (methods.size() != 1) return ModCommand.nop();
    return ModCommand.psiUpdate(methods.getFirst(), (method, updater) -> {
      CreateFromUsageUtils.setupMethodBody(method, updater);
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        CreateFromUsageUtils.setupEditor(body, updater);
      }
    });
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
