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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class EditContractIntention extends BaseIntentionAction implements LowPriorityAction {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Edit method contract";
  }

  @Nullable
  private static PsiMethod getTargetMethod(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner =  AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner instanceof PsiMethod &&
        (!owner.getManager().isInProject(owner) || CodeStyleSettingsManager.getSettings(project).USE_EXTERNAL_ANNOTATIONS)) {
      PsiElement original = owner.getOriginalElement();
      return original instanceof PsiMethod ? (PsiMethod)original : (PsiMethod)owner;
    }
    return null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiMethod method = getTargetMethod(project, editor, file);
    if (method != null) {
      boolean hasContract = ControlFlowAnalyzer.findContractAnnotation(method) != null;
      setText(hasContract ? "Edit method contract of '" + method.getName() + "'" : "Add method contract to '" + method.getName() + "'");
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiMethod method = getTargetMethod(project, editor, file);
    assert method != null;
    Contract existingAnno = AnnotationUtil.findAnnotationInHierarchy(method, Contract.class);
    String oldContract = existingAnno == null ? null : existingAnno.value();
    String prompt =
      "<html>Please specify the contract text<p>" +
      "Example: <code>_, null -> false</code><br>" +
      "<small>See intention action description for more details</small></html>";
    String newContract = Messages.showInputDialog(project, prompt, "Edit Method Contract", null, oldContract, new InputValidatorEx() {
      @Nullable
      @Override
      public String getErrorText(String inputString) {
        if (StringUtil.isEmpty(inputString)) return null;

        return ContractInspection.checkContract(method, inputString);
      }

      @Override
      public boolean checkInput(String inputString) {
        return getErrorText(inputString) == null;
      }

      @Override
      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    });
    if (newContract == null) return;

    AccessToken token = WriteAction.start();
    try {
      ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
      manager.deannotate(method, ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
      if (!StringUtil.isEmpty(newContract)) {
        PsiAnnotation mockAnno = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@Foo(\"" + newContract + "\")", null);
        manager.annotateExternally(method, ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT, file,
                                   mockAnno.getParameterList().getAttributes());
      }
    }
    finally {
      token.finish();
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
