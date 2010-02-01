/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.extractmethod;

import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ExtractMethodDialog extends DialogWrapper implements ExtractMethodSettings {
  private JPanel myContentPane;
  private ParameterTablePanel myParametersPanel;
  private JTextField myMethodNameTextField;
  private JTextArea mySignaturePreviewTextArea;
  private JTextArea myOutputVariablesTextArea;
  private final String myDefaultName;
  private final ExtractMethodValidator myValidator;
  private final ExtractMethodDecorator myDecorator;

  private VariableData[] myVariableData;
  private Map<String, VariableData> myVariablesMap;

  private final List<String> myArguments;
  private final ArrayList<String> myOutputVariables;

  public ExtractMethodDialog(final Project project,
                             final String defaultName,
                             final CodeFragment fragment,
                             final ExtractMethodValidator validator,
                             final ExtractMethodDecorator decorator) {
    super(project, true);
    myDefaultName = defaultName;
    CodeFragment fragment1 = fragment;
    myValidator = validator;
    myDecorator = decorator;
    myArguments = new ArrayList<String>(fragment1.getInputVariables());
    Collections.sort(myArguments);
    myOutputVariables = new ArrayList<String>(fragment1.getOutputVariables());
    Collections.sort(myOutputVariables);
    setModal(true);
    setTitle(RefactoringBundle.message("extract.method.title"));
    init();
  }

  @Override
  protected void init() {
    super.init();
    // Set default name and select it
    myMethodNameTextField.setText(myDefaultName);
    myMethodNameTextField.setSelectionStart(0);
    myMethodNameTextField.setSelectionStart(myDefaultName.length());
    myMethodNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateOutputVariables();
        updateSignature();
        updateOkStatus();
      }
    });


    myVariableData = createVariableData(myArguments);
    myVariablesMap = createVariableMap(myVariableData);
    myParametersPanel.setVariableData(myVariableData);
    myParametersPanel.init();

    updateOutputVariables();
    updateSignature();
    updateOkStatus();
  }

  public JComponent getPreferredFocusedComponent() {
    return myMethodNameTextField;
  }

  public static VariableData[] createVariableData(final List<String> args) {
    final VariableData[] datas = new VariableData[args.size()];
    for (int i = 0; i < args.size(); i++) {
      final VariableData data = new VariableData();
      final String name = args.get(i);
      data.originalName = name;
      data.name = name;
      data.passAsParameter = true;
      datas[i] = data;
    }
    return datas;
  }

  public static Map<String, VariableData> createVariableMap(final VariableData[] data) {
    final HashMap<String, VariableData> map = new HashMap<String, VariableData>();
    for (VariableData variableData : data) {
      map.put(variableData.getOriginalName(), variableData);
    }
    return map;
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doOKAction() {
    final String error = myValidator.check(getMethodName());
    if (error!=null){
      Messages.showInfoMessage(error, RefactoringBundle.message("error.title"));
      return;
    }
    super.doOKAction();
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("refactoring.extractMethod");
  }

  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  private void createUIComponents() {
    myParametersPanel = new ParameterTablePanel(myValidator){
      protected void doCancelAction() {
        ExtractMethodDialog.this.doCancelAction();
      }

      protected void doEnterAction() {
        doOKAction();
      }

      protected void updateSignature() {
        updateOutputVariables();
        ExtractMethodDialog.this.updateSignature();
      }
    };
  }

  private void updateOutputVariables() {
    final StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String variable : myOutputVariables) {
      if (myVariablesMap!=null){
        final VariableData data = myVariablesMap.get(variable);
        final String outputName = data != null ? data.getName() : variable;
        if (first){
          first = false;
        } else {
          builder.append(", ");
        }
        builder.append(outputName);
      }
    }
    myOutputVariablesTextArea.setText(builder.length() > 0 ? builder.toString() : "None");
  }

  private void updateSignature() {
    mySignaturePreviewTextArea.setText(myDecorator.createMethodPreview(getMethodName(), myVariableData));
  }

  private void updateOkStatus() {
    setOKActionEnabled(myValidator.isValidName(getMethodName()));
  }

  public String getMethodName() {
    return myMethodNameTextField.getText().trim();
  }

  public VariableData[] getVariableData() {
    return myVariableData;
  }

}