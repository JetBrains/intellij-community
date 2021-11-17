// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
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
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.EditorTextField;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Enumeration;

public class ExtractMethodObjectDialog extends DialogWrapper implements AbstractExtractDialog {
  private static final String INDENT = "    ";

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
  private MethodSignatureComponent mySignatureArea;
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

    setTitle(JavaRefactoringBundle.message("extract.method.object"));

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

  @NotNull
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
  protected String getHelpId() {
    return HelpID.EXTRACT_METHOD_OBJECT;
  }

  @Override
  protected void doOKAction() {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (myCreateInnerClassRb.isSelected()) {
      final PsiClass innerClass = myTargetClass.findInnerClassByName(myInnerClassName.getText(), false);
      if (innerClass != null) {
        String innerClassDefinedMessage = JavaRefactoringBundle.message("refactoring.extract.method.inner.class.defined",
                                                                        myInnerClassName.getText(), myTargetClass.getName());
        conflicts.putValue(innerClass, innerClassDefinedMessage);
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
    boolean enabled = myInputVariables.length > 0 && myInputVariables[myInputVariables.length - 1].type instanceof PsiArrayType;
    if (myCreateInnerClassRb.isSelected()) {
      myCbMakeVarargs.setEnabled(enabled);
    }
    else {
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

  @NotNull
  @Override
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
    myCreateInnerClassRb.setSelected(true);

    ActionListener enableDisableListener = e -> enable(myCreateInnerClassRb.isSelected());
    myCreateInnerClassRb.addActionListener(enableDisableListener);
    myCreateAnonymousClassWrapperRb.addActionListener(enableDisableListener);
    myCreateAnonymousClassWrapperRb.setEnabled(!myMultipleExitPoints);

    myFoldCb.setSelected(myVariableData.isFoldingSelectedByDefault());
    myFoldCb.setVisible(myVariableData.isFoldable());
    myVariableData.setFoldingAvailable(myFoldCb.isSelected());
    myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[0]);
    myFoldCb.addActionListener(e -> {
      myVariableData.setFoldingAvailable(myFoldCb.isSelected());
      myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[0]);
      myParametersTableContainer.removeAll();
      myParametersTableContainer.add(createParametersPanel(), BorderLayout.CENTER);
      myParametersTableContainer.revalidate();
      updateSignature();
      updateVarargsEnabled();
    });
    myParametersTableContainer.add(createParametersPanel(), BorderLayout.CENTER);

    ActionListener updateSignatureListener = e -> {
      updateSignature();
      IdeFocusManager.getInstance(myProject).requestFocus(myCreateInnerClassRb.isSelected() ? myInnerClassName :  myMethodName, false);
    };

    if (myStaticFlag || myCanBeStatic) {
      myCbMakeStatic.setEnabled(!myStaticFlag);
      myCbMakeStatic.setSelected(myStaticFlag);
      myCbMakeStatic.addActionListener(updateSignatureListener);
    }
    else {
      myCbMakeStatic.setSelected(false);
      myCbMakeStatic.setEnabled(false);
    }

    updateVarargsEnabled();

    myCbMakeVarargs.setSelected(myWasStatic);
    myCbMakeVarargs.addActionListener(updateSignatureListener);

    myCbMakeVarargsAnonymous.setSelected(myWasStatic);
    myCbMakeVarargsAnonymous.addActionListener(updateSignatureListener);

    DocumentListener nameListener = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        update();
      }
    };
    myInnerClassName.getDocument().addDocumentListener(nameListener);
    myMethodName.getDocument().addDocumentListener(nameListener);

    myPrivateRadioButton.setSelected(true);

    myCreateInnerClassRb.addActionListener(updateSignatureListener);
    myCreateAnonymousClassWrapperRb.addActionListener(updateSignatureListener);

    Enumeration<AbstractButton> visibilities = myVisibilityGroup.getElements();
    while (visibilities.hasMoreElements()) {
      visibilities.nextElement().addActionListener(updateSignatureListener);
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
    final ParameterTablePanel panel = new ParameterTablePanel(myProject, myInputVariables, myElementsToExtract) {
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
    panel.setMinimumSize(new Dimension(100, 75));
    return panel;
  }

  protected boolean isUsedAfter(PsiVariable variable) {
    return false;
  }

  protected void updateSignature() {
    if (mySignatureArea != null) {
      mySignatureArea.setText(getSignature().toString());
    }
  }

  private StringBuilder getSignature() {
    StringBuilder buffer = new StringBuilder();
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
      methodSignature(buffer);
      buffer.append("\n}");
    }
    else {
      buffer.append("new Object(){\n");
      buffer.append(INDENT);
      buffer.append("private ");
      buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myMethodName.getText());
      methodSignature(buffer);
      buffer.append("\n}.");
      buffer.append(myMethodName.getText());
      buffer.append("(");
      buffer.append(StringUtil.join(myInputVariables, variableData -> variableData.name, ", "));
      buffer.append(")");
    }

    return buffer;
  }

  private void methodSignature(StringBuilder buffer) {
    buffer.append("(");
    int count = 0;
    final String indent = "    ";
    for (int i = 0; i < myInputVariables.length; i++) {
      VariableData data = myInputVariables[i];
      if (data.passAsParameter) {
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

  private void createUIComponents() {
    mySignatureArea = new MethodSignatureComponent("", myProject, JavaFileType.INSTANCE);
    mySignatureArea.setPreferredSize(JBUI.size(500, 100));
    mySignatureArea.setMinimumSize(JBUI.size(500, 100));
  }
}