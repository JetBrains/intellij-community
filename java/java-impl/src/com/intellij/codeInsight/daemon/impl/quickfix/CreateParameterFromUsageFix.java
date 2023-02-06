// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreateParameterFromUsageFix extends CreateVarFromUsageFix {
  private static final Logger LOG = Logger.getInstance(CreateParameterFromUsageFix.class);

  public CreateParameterFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(myReferenceExpression.isQualified()) return false;
    PsiElement scope = myReferenceExpression;
    do {
      scope = PsiTreeUtil.getParentOfType(scope, PsiMethod.class, PsiClass.class);
      if (!(scope instanceof PsiAnonymousClass)) {
        return scope instanceof PsiMethod &&
               ((PsiMethod)scope).getParameterList().isPhysical();
      }
    }
    while (true);
  }

    @Override
    public String getText(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.PARAMETER.object(), varName);
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    if (method == null) return IntentionPreviewInfo.EMPTY;
    List<ParameterInfoImpl> infos = getParameterInfos(method);
    String newParameters = "(" + StringUtil.join(infos, i -> i.getTypeText() + " " + i.getName(), ", ") + ")";
    StringBuilder newText = new StringBuilder();
    for (PsiElement child : method.getChildren()) {
      newText.append(child instanceof PsiParameterList ? newParameters : child.getText());
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, method.getText(), newText.toString());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    LOG.assertTrue(method != null);
    method = CommonJavaRefactoringUtil.chooseEnclosingMethod(method);
    if (method == null) return;

    method = SuperMethodWarningUtil.checkSuperMethod(method);
    if (method == null) return;

    final List<ParameterInfoImpl> parameterInfos = getParameterInfos(method);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ParameterInfoImpl[] array = parameterInfos.toArray(new ParameterInfoImpl[0]);
      String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(method.getModifierList()));
      var processor = JavaRefactoringFactory.getInstance(project)
        .createChangeSignatureProcessor(method, false, modifier, method.getName(), method.getReturnType(), array, null, null, null, null);
      processor.run();
    }
    else {
      try {
        JavaChangeSignatureDialog dialog =
          JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, true, myReferenceExpression);
        dialog.setParameterInfos(parameterInfos);
        if (dialog.showAndGet()) {
          final String varName = myReferenceExpression.getReferenceName();
          for (ParameterInfoImpl info : parameterInfos) {
            if (info.isNew()) {
              final String newParamName = info.getName();
              if (!Comparing.strEqual(varName, newParamName)) {
                final PsiExpression newExpr =
                  JavaPsiFacade.getElementFactory(project).createExpressionFromText(newParamName, method);
                WriteCommandAction.writeCommandAction(project).run(() -> {
                  final PsiReferenceExpression[] refs =
                    CreateFromUsageUtils.collectExpressions(myReferenceExpression, PsiMember.class, PsiFile.class);
                  for (PsiReferenceExpression ref : refs) {
                    ref.replace(newExpr.copy());
                  }
                });
              }
              break;
            }
          }
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @NotNull
  private List<ParameterInfoImpl> getParameterInfos(PsiMethod method) {
    final String parameterName = myReferenceExpression.getReferenceName();
    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    final List<ParameterInfoImpl> parameterInfos =
      new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
    ParameterInfoImpl parameterInfo =
      ParameterInfoImpl.createNew().withName(parameterName).withType(expectedTypes[0]).withDefaultValue(parameterName);
    if (!method.isVarArgs()) {
      parameterInfos.add(parameterInfo);
    }
    else {
      parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
    }
    return parameterInfos;
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.parameter.from.usage.family");
  }
}
