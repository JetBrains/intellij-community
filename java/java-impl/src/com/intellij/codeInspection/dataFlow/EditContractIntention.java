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
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.InferredAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * @author peter
 */
public class EditContractIntention extends BaseIntentionAction implements LowPriorityAction {
  private static final String ourPrompt = "<html>Please specify the contract text<p>" +
                                          "Example: <code>_, null -> false</code><br>" +
                                          "<small>See intention action description for more details</small></html>";

  @NotNull
  @Override
  public String getFamilyName() {
    return "Edit method contract";
  }

  @Nullable
  private static PsiMethod getTargetMethod(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner =  AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner instanceof PsiMethod && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
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
    boolean oldPure = existingAnno != null && existingAnno.pure();

    JBTextField contractText = new JBTextField(oldContract);
    JCheckBox pureCB = createPureCheckBox(oldPure);
    DialogBuilder builder = createDialog(project, contractText, pureCB);
    contractText.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String error = getErrorMessage(contractText.getText(), method);
        builder.setOkActionEnabled(error == null);
        builder.setErrorText(error);
      }
    });
    if (builder.showAndGet()) {
      updateContract(method, contractText.getText(), pureCB.isSelected());
    }
  }

  private static DialogBuilder createDialog(@NotNull Project project, JBTextField contractText, JCheckBox pureCB) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(Messages.configureMessagePaneUi(new JTextPane(), ourPrompt), BorderLayout.NORTH);
    panel.add(contractText, BorderLayout.CENTER);
    panel.add(pureCB, BorderLayout.SOUTH);

    DialogBuilder builder = new DialogBuilder(project).setNorthPanel(panel).title("Edit Method Contract");
    builder.setPreferredFocusComponent(contractText);
    return builder;
  }

  private static JCheckBox createPureCheckBox(boolean selected) {
    JCheckBox pureCB = new NonFocusableCheckBox("Method is pure (has no side effects)");
    pureCB.setMnemonic('p');
    pureCB.setSelected(selected);
    return pureCB;
  }

  private static void updateContract(PsiMethod method, String contract, boolean pure) {
    Project project = method.getProject();
    WriteAction.run(() -> {
      ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
      manager.deannotate(method, ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
      PsiAnnotation mockAnno = InferredAnnotationsManagerImpl.createContractAnnotation(project, pure, contract);
      if (mockAnno != null) {
        manager.annotateExternally(method, ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT, method.getContainingFile(),
                                   mockAnno.getParameterList().getAttributes());
      }
    });
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Nullable
  private static String getErrorMessage(String contract, PsiMethod method) {
    return StringUtil.isEmpty(contract) ? null : ContractInspection.checkContract(method, contract);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
