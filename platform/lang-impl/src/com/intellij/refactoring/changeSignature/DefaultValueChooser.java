// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class DefaultValueChooser extends DialogWrapper{
  private JRadioButton myLeaveBlankRadioButton;
  private JRadioButton myFeelLuckyRadioButton;
  private JLabel myFeelLuckyDescription;
  private JRadioButton myUseValueRadioButton;
  private EditorTextField myValueEditor;
  private JPanel myWholePanel;
  private JLabel myBlankDescription;

  public DefaultValueChooser(Project project, String name, String defaultValue) {
    super(project);
    RadioUpDownListener.installOn(myLeaveBlankRadioButton, myFeelLuckyRadioButton, myUseValueRadioButton);
    final ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myValueEditor.setEnabled(myUseValueRadioButton.isSelected());
        if (myUseValueRadioButton.isSelected()) {
          myValueEditor.selectAll();
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myValueEditor, true));
        }
      }
    };
    myLeaveBlankRadioButton.addActionListener(actionListener);
    myFeelLuckyRadioButton.addActionListener(actionListener);
    myUseValueRadioButton.addActionListener(actionListener);
    setTitle(RefactoringBundle.message("change.signature.default.value.chooser.title", name));
    myLeaveBlankRadioButton.setSelected(true);
    myValueEditor.setEnabled(false);
    myFeelLuckyDescription.setText(RefactoringBundle.message("change.signature.default.value.description"));
    myFeelLuckyDescription.setUI(new MultiLineLabelUI());
    myBlankDescription.setUI(new MultiLineLabelUI());
    myValueEditor.setText(defaultValue);
    init();
  }

  public boolean feelLucky() {
    return myFeelLuckyRadioButton.isSelected();
  }


  public String getDefaultValue() {
    if (myLeaveBlankRadioButton.isSelected()) {
      return "";
    }
    return myValueEditor.getText();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLeaveBlankRadioButton;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myWholePanel;
  }
}
