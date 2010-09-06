/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;


public class ExtractMethodDialog extends AbstractExtractDialog {
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private final boolean myCanBeStatic;
  private final PsiElement[] myElementsToExtract;
  private final String myHelpId;

  private final EditorTextField myNameField;
  private final JTextArea mySignatureArea;
  private final JCheckBox myCbMakeStatic;
  protected JCheckBox myCbMakeVarargs;
  private JCheckBox myCbChainedConstructor;

  private final InputVariables myVariableData;
  private final PsiClass myTargetClass;
  private JavaVisibilityPanel myVisibilityPanel;

  private boolean myDefaultVisibility = true;
  private boolean myChangingVisibility;

  private final JCheckBox myFoldCb = new NonFocusableCheckBox(RefactoringBundle.message("declare.folded.parameters"));
  public JPanel myCenterPanel;
  public JPanel myParamTabel;
  private ParameterTablePanel.VariableData[] myInputVariables;

  public ExtractMethodDialog(Project project,
                             PsiClass targetClass, final InputVariables inputVariables, PsiType returnType,
                             PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic,
                             final boolean canBeChainedConstructor,
                             String initialMethodName,
                             String title,
                             String helpId,
                             final PsiElement[] elementsToExtract) {
    super(project);
    myProject = project;
    myTargetClass = targetClass;
    myReturnType = returnType;
    myTypeParameterList = typeParameterList;
    myExceptions = exceptions;
    myStaticFlag = isStatic;
    myCanBeStatic = canBeStatic;
    myElementsToExtract = elementsToExtract;
    myVariableData = inputVariables;
    myHelpId = helpId;
    setTitle(title);

    // Create UI components

    myNameField = new EditorTextField(initialMethodName);
    //myTfName.setText(initialMethodName);

    int height = myVariableData.getInputVariables().size() + 2;
    if (myExceptions.length > 0) {
      height += myExceptions.length + 1;
    }
    mySignatureArea = new JTextArea(height, 30);
    myCbMakeStatic = new NonFocusableCheckBox();
    myCbMakeStatic.setText(RefactoringBundle.message("declare.static.checkbox"));
    if (canBeChainedConstructor) {
      myCbChainedConstructor = new NonFocusableCheckBox(RefactoringBundle.message("extract.chained.constructor.checkbox"));
    }

    // Initialize UI


  }

  protected boolean areTypesDirected() {
    return true;
  }

  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    return myCanBeStatic && myCbMakeStatic.isSelected();
  }

  public boolean isChainedConstructor() {
    return myCbChainedConstructor != null && myCbChainedConstructor.isSelected();  
  }

  protected Action[] createActions() {
    if (myHelpId != null) {
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    } else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  public String getChosenMethodName() {
    return myNameField.getText();
  }

  public ParameterTablePanel.VariableData[] getChosenParameters() {
    return myInputVariables;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  protected void doOKAction() {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    checkMethodConflicts(conflicts);
    if (!conflicts.isEmpty()) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      if (!conflictsDialog.isOK()){
        if (conflictsDialog.isShowConflicts()) close(CANCEL_EXIT_CODE);
        return;
      }
    }

    if (myCbMakeVarargs != null && myCbMakeVarargs.isSelected()) {
      final ParameterTablePanel.VariableData data = myInputVariables[myInputVariables.length - 1];
      if (data.type instanceof PsiArrayType) {
        data.type = new PsiEllipsisType(((PsiArrayType)data.type).getComponentType());
      }
    }
    super.doOKAction();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("extract.method.method.panel.border")));

    JLabel nameLabel = new JLabel();
    nameLabel.setText(RefactoringBundle.message("name.prompt"));
    panel.add(nameLabel);

    panel.add(myNameField);
    nameLabel.setLabelFor(myNameField);

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        update();
      }
    });

    setOKActionEnabled(false);
    panel.add(myCbMakeStatic);
    if (myStaticFlag || myCanBeStatic) {
      myCbMakeStatic.setEnabled(!myStaticFlag);
      myCbMakeStatic.setSelected(myStaticFlag);
      myCbMakeStatic.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateSignature();
        }
      });
    } else {
      myCbMakeStatic.setSelected(false);
      myCbMakeStatic.setEnabled(false);
    }

    myFoldCb.setSelected(myVariableData.isFoldingSelectedByDefault());
    myFoldCb.setVisible(myVariableData.isFoldable());
    myVariableData.setFoldingAvailable(myFoldCb.isSelected());
    myInputVariables = myVariableData.getInputVariables().toArray(new ParameterTablePanel.VariableData[myVariableData.getInputVariables().size()]);
    myFoldCb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myVariableData.setFoldingAvailable(myFoldCb.isSelected());
        myInputVariables = myVariableData.getInputVariables().toArray(new ParameterTablePanel.VariableData[myVariableData.getInputVariables().size()]);
        updateVarargsEnabled();
        createParametersPanel();
        updateSignature();
      }
    });
    panel.add(myFoldCb);

    boolean canBeVarargs = false;
    for (ParameterTablePanel.VariableData data : myInputVariables) {
      canBeVarargs |= data.type instanceof PsiArrayType;
    }
    if (myVariableData.isFoldable()) {
      canBeVarargs |= myVariableData.isFoldingSelectedByDefault();
    }
    if (canBeVarargs) {
      myCbMakeVarargs = new NonFocusableCheckBox(RefactoringBundle.message("declare.varargs.checkbox"));
      updateVarargsEnabled();
      myCbMakeVarargs.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateSignature();
        }
      });
      myCbMakeVarargs.setSelected(false);
      panel.add(myCbMakeVarargs);
    }
    if (myCbChainedConstructor != null) {
      panel.add(myCbChainedConstructor);
      myCbChainedConstructor.addItemListener(new ItemListener() {
        public void itemStateChanged(final ItemEvent e) {
          if (myDefaultVisibility) {
            myChangingVisibility = true;
            try {
              if (isChainedConstructor()) {
                myVisibilityPanel.setVisibility(VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList()));
              }
              else {
                myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
              }
            }
            finally {
              myChangingVisibility = false;
            }
          }
          update();
        }
      });
    }
    setOKActionEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(myNameField.getText()));

    return panel;
  }

  private void updateVarargsEnabled() {
    if (myCbMakeVarargs != null) {
      myCbMakeVarargs.setEnabled(myInputVariables[myInputVariables.length - 1].type instanceof PsiArrayType);
    }
  }

  private void update() {
    myNameField.setEnabled(!isChainedConstructor());
    if (myCbMakeStatic != null) {
      myCbMakeStatic.setEnabled(!myStaticFlag && myCanBeStatic && !isChainedConstructor());
    }
    updateSignature();
    setOKActionEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(myNameField.getText()) ||
                       isChainedConstructor());
  }

  public String getVisibility() {
    return myVisibilityPanel.getVisibility();
  }


  protected JComponent createCenterPanel() {
    myVisibilityPanel = new JavaVisibilityPanel(false, false);
    myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
    myVisibilityPanel.addListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateSignature();
        if (!myChangingVisibility) {
          myDefaultVisibility = false;
        }
      }
    });
    myCenterPanel = new JPanel(new BorderLayout());
    createParametersPanel();
    myCenterPanel.add(createSignaturePanel(), BorderLayout.SOUTH);
    myCenterPanel.add(myVisibilityPanel, BorderLayout.EAST);
    return myCenterPanel;
  }

  protected boolean isOutputVariable(PsiVariable var) {
    return false;
  }

  private void createParametersPanel() {
    if (myParamTabel != null) {
      myCenterPanel.remove(myParamTabel);
    }
    myParamTabel = new ParameterTablePanel(myProject, myInputVariables, myElementsToExtract) {
      protected void updateSignature() {
        updateVarargsEnabled();
        ExtractMethodDialog.this.updateSignature();
      }

      protected void doEnterAction() {
        clickDefaultButton();
      }

      protected void doCancelAction() {
        ExtractMethodDialog.this.doCancelAction();
      }

      protected boolean areTypesDirected() {
        return ExtractMethodDialog.this.areTypesDirected();
      }

      @Override
      protected boolean isUsedAfter(PsiVariable variable) {
        return isOutputVariable(variable);
      }
    };
    myParamTabel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("parameters.border.title")));
    myCenterPanel.add(myParamTabel, BorderLayout.CENTER);
  }

  private JComponent createSignaturePanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("signature.preview.border.title")));

    mySignatureArea.setEditable(false);
    mySignatureArea.setBackground(getContentPane().getBackground());
    panel.add(mySignatureArea, BorderLayout.CENTER);

    updateSignature();
    Dimension size = mySignatureArea.getPreferredSize();
    mySignatureArea.setMaximumSize(size);
    mySignatureArea.setMinimumSize(size);
    return panel;
  }

  protected void updateSignature() {
    if (mySignatureArea == null) return;
    @NonNls StringBuffer buffer = getSignature();
    mySignatureArea.setText(buffer.toString());
  }

  protected StringBuffer getSignature() {
    @NonNls StringBuffer buffer = new StringBuffer();
    final String visibilityString = VisibilityUtil.getVisibilityString(myVisibilityPanel.getVisibility());
    buffer.append(visibilityString);
    if (buffer.length() > 0) {
      buffer.append(" ");
    }
    if (isMakeStatic() && !isChainedConstructor()) {
      buffer.append("static ");
    }
    if (myTypeParameterList != null) {
      buffer.append(myTypeParameterList.getText());
      buffer.append(" ");
    }

    if (isChainedConstructor()) {
      buffer.append(myTargetClass.getName());
    }
    else {
      buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myNameField.getText());
    }
    buffer.append("(");
    int count = 0;
    final String INDENT = "    ";
    final ParameterTablePanel.VariableData[] datas = myInputVariables;
    for (int i = 0; i < datas.length;i++) {
      ParameterTablePanel.VariableData data = datas[i];
      if (data.passAsParameter) {
        //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
        //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
        PsiType type = data.type;
        if (i == datas.length - 1 && type instanceof PsiArrayType && myCbMakeVarargs.isSelected()) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType());
        }

        String typeText = type.getPresentableText();
        if (count > 0) {
          buffer.append(",");
        }
        buffer.append("\n");
        buffer.append(INDENT);
        buffer.append(typeText);
        buffer.append(" ");
        buffer.append(data.name);
        count++;
      }
    }
    if (count > 0) {
      buffer.append("\n");
    }
    buffer.append(")");
    if (myExceptions.length > 0) {
      buffer.append("\n");
      buffer.append("throws\n");
      for (PsiType exception : myExceptions) {
        buffer.append(INDENT);
        buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
        buffer.append("\n");
      }
    }
    return buffer;
  }

  protected void checkMethodConflicts(MultiMap<PsiElement, String> conflicts) {
    PsiMethod prototype;
    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      prototype = factory.createMethod(
              myNameField.getText().trim(),
              myReturnType
      );
      if (myTypeParameterList != null) prototype.getTypeParameterList().replace(myTypeParameterList);
      for (ParameterTablePanel.VariableData data : myInputVariables) {
        if (data.passAsParameter) {
          prototype.getParameterList().add(factory.createParameter(data.name, data.type));
        }
      }
      // set the modifiers with which the method is supposed to be created
      PsiUtil.setModifierProperty(prototype, PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      return;
    }

    ConflictsUtil.checkMethodConflicts(
      myTargetClass,
      null,
      prototype, conflicts);
  }
}
