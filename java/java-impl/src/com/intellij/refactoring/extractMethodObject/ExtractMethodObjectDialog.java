/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethodObject;

import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.EditorTextField;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;


public class ExtractMethodObjectDialog extends DialogWrapper implements AbstractExtractDialog {
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private final boolean myCanBeStatic;
  private final PsiElement[] myElementsToExtract;
  private final boolean myMultipleExitPoints;

  private final InputVariables myVariableData;
  private final PsiClass myTargetClass;
  private final boolean myWasStatic;


  private JRadioButton myCreateInnerClassRb;
  private JRadioButton myCreateAnonymousClassWrapperRb;
  private JTextArea mySignatureArea;
  private JCheckBox myCbMakeStatic;
  private JCheckBox myCbMakeVarargs;
  private JCheckBox myCbMakeVarargsAnonymous;

  private JPanel myWholePanel;
  private JPanel myParametersTableContainer;
  private JRadioButton myPrivateRadioButton;
  private JRadioButton myProtectedRadioButton;
  private JRadioButton myPackageLocalRadioButton;
  private JRadioButton myPublicRadioButton;

  private EditorTextField myInnerClassName;
  private EditorTextField myMethodName;

  private JPanel myInnerClassPanel;
  private JPanel myAnonymousClassPanel;
  private JCheckBox myFoldCb;
  private ButtonGroup myVisibilityGroup;
  private VariableData[] myInputVariables;


  public ExtractMethodObjectDialog(Project project, PsiClass targetClass, final InputVariables inputVariables, PsiType returnType,
                                   PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic,
                                   final PsiElement[] elementsToExtract, final boolean multipleExitPoints) {
    super(project, true);
    myProject = project;
    myTargetClass = targetClass;
    myReturnType = returnType;
    myTypeParameterList = typeParameterList;
    myExceptions = exceptions;
    myStaticFlag = isStatic;
    myCanBeStatic = canBeStatic;
    myElementsToExtract = elementsToExtract;
    myMultipleExitPoints = multipleExitPoints;

    boolean canBeVarargs = false;
    for (VariableData data : inputVariables.getInputVariables()) {
      canBeVarargs |= data.type instanceof PsiArrayType;
    }
    canBeVarargs |= inputVariables.isFoldable()  && inputVariables.isFoldingSelectedByDefault();
    myWasStatic = canBeVarargs;

    myVariableData = inputVariables;

    setTitle(ExtractMethodObjectProcessor.REFACTORING_NAME);

    // Create UI components


    myCbMakeVarargs.setVisible(canBeVarargs);
    myCbMakeVarargsAnonymous.setVisible(canBeVarargs);

    // Initialize UI
     init();

  }

  @Override
  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    if (!myCanBeStatic) return false;
    return myCbMakeStatic.isSelected();
  }

  @Override
  public boolean isChainedConstructor() {
    return false;
  }

  @Override
  public PsiType getReturnType() {
    return null;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public String getChosenMethodName() {
    return myCreateInnerClassRb.isSelected() ? myInnerClassName.getText() : myMethodName.getText();
  }

  @Override
  public VariableData[] getChosenParameters() {
    return myInputVariables;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myInnerClassName;
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXTRACT_METHOD_OBJECT);
  }

  @Override
  protected void doOKAction() {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (myCreateInnerClassRb.isSelected()) {
      final PsiClass innerClass = myTargetClass.findInnerClassByName(myInnerClassName.getText(), false);
      if (innerClass != null) {
        conflicts.putValue(innerClass, "Inner class " + myInnerClassName.getText() + " already defined in class " + myTargetClass.getName());
      }
    }
    if (conflicts.size() > 0) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      if (!conflictsDialog.showAndGet()) {
        if (conflictsDialog.isShowConflicts()) close(CANCEL_EXIT_CODE);
        return;
      }
    }

    final JCheckBox makeVarargsCb = myCreateInnerClassRb.isSelected() ? myCbMakeVarargs : myCbMakeVarargsAnonymous;
    if (makeVarargsCb != null && makeVarargsCb.isSelected()) {
      final VariableData data = myInputVariables[myInputVariables.length - 1];
      if (data.type instanceof PsiArrayType) {
        data.type = new PsiEllipsisType(((PsiArrayType)data.type).getComponentType());
      }
    }
    super.doOKAction();
  }

  private void updateVarargsEnabled() {
    final boolean enabled = myInputVariables.length > 0 && myInputVariables[myInputVariables.length - 1].type instanceof PsiArrayType;
    if (myCreateInnerClassRb.isSelected()) {
      myCbMakeVarargs.setEnabled(enabled);
    } else {
      myCbMakeVarargsAnonymous.setEnabled(enabled);
    }
  }

  private void update() {
    myCbMakeStatic.setEnabled(myCreateInnerClassRb.isSelected() && myCanBeStatic && !myStaticFlag);
    updateSignature();
    final PsiNameHelper helper = PsiNameHelper.getInstance(myProject);
    setOKActionEnabled((myCreateInnerClassRb.isSelected() && helper.isIdentifier(myInnerClassName.getText())) ||
                        (!myCreateInnerClassRb.isSelected() && helper.isIdentifier(myMethodName.getText())));
  }

  @Override
  @NotNull
  public String getVisibility() {
    if (myPublicRadioButton.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myPackageLocalRadioButton.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myProtectedRadioButton.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    return PsiModifier.PRIVATE;
  }


  @Override
  protected JComponent createCenterPanel() {
    mySignatureArea.setEditable(false);
    myCreateInnerClassRb.setSelected(true);

    final ActionListener enableDisableListener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        enable(myCreateInnerClassRb.isSelected());
      }
    };
    myCreateInnerClassRb.addActionListener(enableDisableListener);
    myCreateAnonymousClassWrapperRb.addActionListener(enableDisableListener);
    myCreateAnonymousClassWrapperRb.setEnabled(!myMultipleExitPoints);

    myFoldCb.setSelected(myVariableData.isFoldingSelectedByDefault());
    myFoldCb.setVisible(myVariableData.isFoldable());
    myVariableData.setFoldingAvailable(myFoldCb.isSelected());
    myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
    myFoldCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myVariableData.setFoldingAvailable(myFoldCb.isSelected());
        myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
        myParametersTableContainer.removeAll();
        myParametersTableContainer.add(createParametersPanel(), BorderLayout.CENTER);
        myParametersTableContainer.revalidate();
        updateSignature();
        updateVarargsEnabled();
      }
    });
    myParametersTableContainer.add(createParametersPanel(), BorderLayout.CENTER);

    final ActionListener updateSugnatureListener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        updateSignature();
        IdeFocusManager.getInstance(myProject).requestFocus(myCreateInnerClassRb.isSelected() ? myInnerClassName :  myMethodName, false);
      }
    };

    if (myStaticFlag || myCanBeStatic) {
      myCbMakeStatic.setEnabled(!myStaticFlag);
      myCbMakeStatic.setSelected(myStaticFlag);

      myCbMakeStatic.addActionListener(updateSugnatureListener);
    } else {
      myCbMakeStatic.setSelected(false);
      myCbMakeStatic.setEnabled(false);
    }

    updateVarargsEnabled();

    myCbMakeVarargs.setSelected(myWasStatic);
    myCbMakeVarargs.addActionListener(updateSugnatureListener);

    myCbMakeVarargsAnonymous.setSelected(myWasStatic);
    myCbMakeVarargsAnonymous.addActionListener(updateSugnatureListener);

    final DocumentAdapter nameListener = new DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        update();
      }
    };
    myInnerClassName.getDocument().addDocumentListener(nameListener);
    myMethodName.getDocument().addDocumentListener(nameListener);

    myPrivateRadioButton.setSelected(true);

    myCreateInnerClassRb.addActionListener(updateSugnatureListener);
    myCreateAnonymousClassWrapperRb.addActionListener(updateSugnatureListener);

    final Enumeration<AbstractButton> visibilities = myVisibilityGroup.getElements();
    while(visibilities.hasMoreElements()) {
      visibilities.nextElement().addActionListener(updateSugnatureListener);
    }

    enable(true);
    return myWholePanel;
  }

  private void enable(boolean innerClassSelected){
    UIUtil.setEnabled(myInnerClassPanel, innerClassSelected, true);
    UIUtil.setEnabled(myAnonymousClassPanel, !innerClassSelected, true);
    update();
  }

  private JComponent createParametersPanel() {
    return new ParameterTablePanel(myProject, myInputVariables, myElementsToExtract) {
      @Override
      protected void updateSignature() {
        updateVarargsEnabled();
        ExtractMethodObjectDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        ExtractMethodObjectDialog.this.doCancelAction();
      }

      @Override
      protected boolean isUsedAfter(PsiVariable variable) {
        return ExtractMethodObjectDialog.this.isUsedAfter(variable);
      }
    };
  }

  protected boolean isUsedAfter(PsiVariable variable) {
    return false;
  }

  protected void updateSignature() {
    if (mySignatureArea == null) return;
    @NonNls StringBuffer buffer = getSignature();
    mySignatureArea.setText(buffer.toString());
  }

  protected StringBuffer getSignature() {
    final String INDENT = "    ";
    @NonNls StringBuffer buffer = new StringBuffer();
    final String visibilityString = VisibilityUtil.getVisibilityString(getVisibility());
    if (myCreateInnerClassRb.isSelected()) {
      buffer.append(visibilityString);
      if (buffer.length() > 0) {
        buffer.append(" ");
      }
      if (isMakeStatic()) {
        buffer.append("static ");
      }
      buffer.append("class ");
      buffer.append(myInnerClassName.getText());
      if (myTypeParameterList != null) {
        buffer.append(myTypeParameterList.getText());
        buffer.append(" ");
      }
      buffer.append("{\n");
      buffer.append(INDENT);
      buffer.append("public ");
      buffer.append(myInnerClassName.getText());
      methodSignature(INDENT, buffer);
      buffer.append("\n}");
    } else {
      buffer.append("new Object(){\n");
      buffer.append(INDENT);
      buffer.append("private ");
      buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myMethodName.getText());
      methodSignature(INDENT, buffer);
      buffer.append("\n}.");
      buffer.append(myMethodName.getText());
      buffer.append("(");
      buffer.append(StringUtil.join(myInputVariables, variableData -> variableData.name, ", "));
      buffer.append(")");
    }

    return buffer;
  }

  private void methodSignature(final String INDENT, final StringBuffer buffer) {
    buffer.append("(");
    int count = 0;
    final String indent = "    ";
    for (int i = 0; i < myInputVariables.length; i++) {
      VariableData data = myInputVariables[i];
      if (data.passAsParameter) {
        //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
        //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
        PsiType type = data.type;
        if (i == myInputVariables.length - 1 && type instanceof PsiArrayType && ((myCreateInnerClassRb.isSelected() && myCbMakeVarargs.isSelected()) || (myCreateAnonymousClassWrapperRb.isSelected() && myCbMakeVarargsAnonymous.isSelected()))) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType());
        }

        String typeText = type.getPresentableText();
        if (count > 0) {
          buffer.append(", ");
        }
        buffer.append("\n");
        buffer.append(indent);
        buffer.append(typeText);
        buffer.append(" ");
        buffer.append(data.name);
        count++;
      }
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
    buffer.append("{}");
  }

  public boolean createInnerClass() {
    return myCreateInnerClassRb.isSelected();
  }
}
