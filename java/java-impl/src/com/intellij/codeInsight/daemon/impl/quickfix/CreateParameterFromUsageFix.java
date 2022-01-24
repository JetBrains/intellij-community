// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
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
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    final String varName = myReferenceExpression.getReferenceName();
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    LOG.assertTrue(method != null);
    method = JavaSpecialRefactoringProvider.getInstance().chooseEnclosingMethod(method);
    if (method == null) return;

    method = SuperMethodWarningUtil.checkSuperMethod(method);
    if (method == null) return;

    final List<ParameterInfoImpl> parameterInfos =
      new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
    ParameterInfoImpl parameterInfo = ParameterInfoImpl.createNew().withName(varName).withType(type).withDefaultValue(varName);
    if (!method.isVarArgs()) {
      parameterInfos.add(parameterInfo);
    }
    else {
      parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ParameterInfoImpl[] array = parameterInfos.toArray(new ParameterInfoImpl[0]);
      String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(method.getModifierList()));
      var provider = JavaSpecialRefactoringProvider.getInstance();
      var processor = provider.getChangeSignatureProcessorWithCallback(
        project, method, false, modifier, method.getName(), method.getReturnType(),
        array, true, null);
      processor.run();
    }
    else {
      try {
        JavaChangeSignatureDialog dialog =
          JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, true, myReferenceExpression);
        dialog.setParameterInfos(parameterInfos);
        if (dialog.showAndGet()) {
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
