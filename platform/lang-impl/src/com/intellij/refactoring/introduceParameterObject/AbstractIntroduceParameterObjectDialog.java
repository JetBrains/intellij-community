// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.AbstractParameterTablePanel;
import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public abstract class AbstractIntroduceParameterObjectDialog<M extends PsiNamedElement,
                                                             P extends ParameterInfo,
                                                             C extends IntroduceParameterObjectClassDescriptor<M, P>,
                                                             V extends AbstractVariableData> extends RefactoringDialog {
  protected @NotNull M mySourceMethod;
  private JPanel myWholePanel;
  private JTextField mySourceMethodTextField;
  protected JCheckBox myDelegateCheckBox;
  private JPanel myParamsPanel;
  private JPanel myParameterClassPanel;

  protected AbstractParameterTablePanel<V> myParameterTablePanel;

  protected abstract String getSourceMethodPresentation();
  protected abstract JPanel createParameterClassPanel();
  protected abstract AbstractParameterTablePanel<V> createParametersPanel();

  protected abstract C createClassDescriptor();

  protected boolean isDelegateCheckboxVisible() {
    return true;
  }

  public AbstractIntroduceParameterObjectDialog(@NotNull M method) {
    super(method.getProject(), true);
    mySourceMethod = method;
    setTitle(RefactoringBundle.message("refactoring.introduce.parameter.object.title"));
  }

  @Override
  protected void doAction() {
    final IntroduceParameterObjectDelegate<M, P, C> delegate = IntroduceParameterObjectDelegate.findDelegate(mySourceMethod);
    final List<P> allMethodParameters = delegate.getAllMethodParameters(mySourceMethod);
    invokeRefactoring(
      new IntroduceParameterObjectProcessor<>(mySourceMethod, createClassDescriptor(), allMethodParameters, keepMethodAsDelegate()));
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (!hasParametersToExtract()) {
      throw new ConfigurationException(LangBundle.message("dialog.message.nothing.found.to.extract"));
    }
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    mySourceMethodTextField.setText(getSourceMethodPresentation());
    mySourceMethodTextField.setEditable(false);

    myDelegateCheckBox.setVisible(isDelegateCheckboxVisible());

    myParameterTablePanel = createParametersPanel();
    myParamsPanel.add(myParameterTablePanel, BorderLayout.CENTER);

    myParameterClassPanel.add(createParameterClassPanel(), BorderLayout.CENTER);
    return myWholePanel;
  }

  protected boolean keepMethodAsDelegate() {
    return myDelegateCheckBox.isSelected();
  }

  public boolean hasParametersToExtract() {
    for (AbstractVariableData info : myParameterTablePanel.getVariableData()) {
      if (info.passAsParameter) {
        return true;
      }
    }
    return false;
  }
}
