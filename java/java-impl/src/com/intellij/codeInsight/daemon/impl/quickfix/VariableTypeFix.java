// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class VariableTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(VariableTypeFix.class);

  private final PsiType myReturnType;
  protected final String myName;

  protected VariableTypeFix(@NotNull PsiVariable variable, @NotNull PsiType toReturn) {
    super(variable);
    myReturnType = GenericsUtil.getVariableTypeByExpressionType(toReturn);
    myName = variable.getName();
  }

  @NotNull
  @Override
  public String getText() {
    PsiType type = getReturnType();
    PsiElement startElement = getStartElement();
    String typeName = startElement == null ? "?" : UsageViewUtil.getType(startElement);
    return QuickFixBundle.message("fix.variable.type.text",
                                  typeName,
                                  myName,
                                  type == null || !type.isValid() ? "???" : type.getPresentableText());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    PsiType type = getReturnType();
    return myVariable.getTypeElement() != null
           && BaseIntentionAction.canModify(myVariable)
           && type != null && type.isValid()
           && !LambdaUtil.notInferredType(type)
           && !TypeConversionUtil.isNullType(type)
           && !TypeConversionUtil.isVoidType(type);
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull final PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    if (changeMethodSignatureIfNeeded(myVariable)) return;
    WriteCommandAction.writeCommandAction(project, file).withName(getText()).run(() -> {
      try {
        myVariable.normalizeDeclaration();
        final PsiTypeElement typeElement = myVariable.getTypeElement();
        LOG.assertTrue(typeElement != null, myVariable.getClass());
        final PsiTypeElement newTypeElement =
          JavaPsiFacade.getElementFactory(file.getProject()).createTypeElement(getReturnType());
        typeElement.replace(newTypeElement);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
        UndoUtil.markPsiFileForUndo(file);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  private boolean changeMethodSignatureIfNeeded(PsiVariable myVariable) {
    if (myVariable instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)myVariable).getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        final PsiMethod psiMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
        if (psiMethod == null) return true;
        final int parameterIndex = method.getParameterList().getParameterIndex((PsiParameter)myVariable);
        if (!FileModificationService.getInstance().prepareFileForWrite(psiMethod.getContainingFile())) return true;
        final ArrayList<ParameterInfoImpl> infos = new ArrayList<>();
        int i = 0;
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
          final boolean changeType = i == parameterIndex;
          infos.add(ParameterInfoImpl.create(i++)
                      .withName(parameter.getName())
                      .withType(changeType ? getReturnType() : parameter.getType()));
        }

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          final JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(psiMethod.getProject(), psiMethod, false, myVariable);
          dialog.setParameterInfos(infos);
          dialog.show();
        }
        else {
          ChangeSignatureProcessor processor = new ChangeSignatureProcessor(psiMethod.getProject(),
                                                                            psiMethod,
                                                                            false, null,
                                                                            psiMethod.getName(),
                                                                            psiMethod.getReturnType(),
                                                                            infos.toArray(new ParameterInfoImpl[0]));
          processor.run();
        }
        return true;
      }
    }
    return false;
  }

  protected PsiType getReturnType() {
    return myReturnType;
  }
}
