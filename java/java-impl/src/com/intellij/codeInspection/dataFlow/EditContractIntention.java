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
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
import com.intellij.util.ui.JBUI;
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
  private static PsiMethod getTargetMethod(Editor editor, PsiFile file) {
    final PsiModifierListOwner owner =  AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner instanceof PsiMethod && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      PsiElement original = owner.getOriginalElement();
      return original instanceof PsiMethod ? (PsiMethod)original : (PsiMethod)owner;
    }
    return null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiMethod method = getTargetMethod(editor, file);
    if (method != null) {
      boolean hasContract = ControlFlowAnalyzer.findContractAnnotation(method) != null;
      setText(hasContract ? "Edit method contract of '" + method.getName() + "'" : "Add method contract to '" + method.getName() + "'");
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiMethod method = getTargetMethod(editor, file);
    assert method != null;
    Contract existingAnno = AnnotationUtil.findAnnotationInHierarchy(method, Contract.class);
    String oldContract = existingAnno == null ? null : existingAnno.value();
    boolean oldPure = existingAnno != null && existingAnno.pure();
    String oldMutates = existingAnno == null ? null : existingAnno.mutates();

    JBTextField contractText = new JBTextField(oldContract);
    JBTextField mutatesText = new JBTextField(oldMutates);
    JCheckBox pureCB = createPureCheckBox(oldPure);
    DialogBuilder builder = createDialog(project, contractText, pureCB, mutatesText);
    DocumentAdapter validator = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String contractError = getContractErrorMessage(contractText.getText(), method);
        if (contractError != null) {
          builder.setOkActionEnabled(false);
          builder.setErrorText(contractError, contractText);
        }
        else {
          String mutatesError = getMutatesErrorMessage(mutatesText.getText(), method);
          if (mutatesError != null) {
            builder.setOkActionEnabled(false);
            builder.setErrorText(mutatesError, mutatesText);
          }
          else {
            builder.setOkActionEnabled(true);
            builder.setErrorText(null);
          }
        }
      }
    };
    Runnable updateControls = () -> {
      if (pureCB.isSelected()) {
        mutatesText.setText("");
        mutatesText.setEnabled(false);
      }
      else {
        mutatesText.setEnabled(true);
      }
    };
    pureCB.addChangeListener(e -> updateControls.run());
    contractText.getDocument().addDocumentListener(validator);
    mutatesText.getDocument().addDocumentListener(validator);
    updateControls.run();
    if (builder.showAndGet()) {
      updateContract(method, contractText.getText(), pureCB.isSelected(), mutatesText.getText());
    }
  }

  private static DialogBuilder createDialog(@NotNull Project project,
                                            JBTextField contractText,
                                            JCheckBox pureCB,
                                            JBTextField mutatesText) {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints constraints =
      new GridBagConstraints(0, 0, 2, 1, 4.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insets(2), 0, 0);
    panel.add(Messages.configureMessagePaneUi(new JTextPane(), ourPrompt), constraints);
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 1;
    constraints.weightx = 1;
    JLabel contractLabel = new JLabel("Contract:");
    contractLabel.setDisplayedMnemonic('c');
    contractLabel.setLabelFor(contractText);
    panel.add(contractLabel, constraints);
    constraints.gridx = 1;
    constraints.weightx = 3;
    panel.add(contractText, constraints);
    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.gridwidth = 2;
    constraints.weightx = 4;
    panel.add(pureCB, constraints);
    panel.add(pureCB, constraints);
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      constraints.gridx = 0;
      constraints.gridy = 3;
      constraints.weightx = 1;
      constraints.gridwidth = 1;
      JLabel mutatesLabel = new JLabel("Mutates:");
      mutatesLabel.setDisplayedMnemonic('m');
      mutatesLabel.setLabelFor(mutatesText);
      panel.add(mutatesLabel, constraints);
      constraints.gridx = 1;
      constraints.weightx = 3;
      panel.add(mutatesText, constraints);
    }

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

  private static void updateContract(PsiMethod method, String contract, boolean pure, String mutates) {
    Project project = method.getProject();
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
    manager.deannotate(method, ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    PsiAnnotation mockAnno = InferredAnnotationsManagerImpl.createContractAnnotation(project, pure, contract, mutates);
    if (mockAnno != null) {
      try {
        manager.annotateExternally(method, ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT, method.getContainingFile(),
                                   mockAnno.getParameterList().getAttributes());
      }
      catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Nullable
  private static String getMutatesErrorMessage(String mutates, PsiMethod method) {
    return StringUtil.isEmpty(mutates) ? null : MutationSignature.checkSignature(mutates, method);
  }

  @Nullable
  private static String getContractErrorMessage(String contract, PsiMethod method) {
    return StringUtil.isEmpty(contract) ? null : ContractInspection.checkContract(method, contract);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
