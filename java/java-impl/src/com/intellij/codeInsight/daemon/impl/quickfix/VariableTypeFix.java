/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
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
  static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix");

  private final PsiType myReturnType;
  protected final String myName;

  public VariableTypeFix(@NotNull PsiVariable variable, PsiType toReturn) {
    super(variable);
    myReturnType = GenericsUtil.getVariableTypeByExpressionType(toReturn);
    myName = variable.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  UsageViewUtil.getType(getStartElement()),
                                  myName,
                                  getReturnType().getCanonicalText());
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
    return myVariable.getTypeElement() != null
        && myVariable.getManager().isInProject(myVariable)
        && getReturnType() != null
        && !LambdaUtil.notInferredType(getReturnType())
        && getReturnType().isValid()
        && !TypeConversionUtil.isNullType(getReturnType())
        && !TypeConversionUtil.isVoidType(getReturnType());
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull final PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    if (changeMethodSignatureIfNeeded(myVariable)) return;
    new WriteCommandAction.Simple(project, getText(), file) {

      @Override
      protected void run() {
        try {
          myVariable.normalizeDeclaration();
          final PsiTypeElement typeElement = myVariable.getTypeElement();
          LOG.assertTrue(typeElement != null, myVariable.getClass());
          final PsiTypeElement newTypeElement = JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createTypeElement(getReturnType());
          typeElement.replace(newTypeElement);
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
          UndoUtil.markPsiFileForUndo(file);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }.execute();
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
          infos.add(new ParameterInfoImpl(i++, parameter.getName(), changeType ? getReturnType() : parameter.getType()));
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
                                                                            infos.toArray(new ParameterInfoImpl[infos.size()]));
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
