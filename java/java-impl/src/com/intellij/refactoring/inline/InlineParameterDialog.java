package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class InlineParameterDialog extends RefactoringMessageDialog {
  private JCheckBox myCreateLocalCheckbox;

  public InlineParameterDialog(String title, String message, String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    super(title, message, helpTopic, iconId, showCancelButton, project);
  }

  protected JComponent createNorthPanel() {
    JComponent superPanel = super.createNorthPanel();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(superPanel, BorderLayout.CENTER);
    myCreateLocalCheckbox = new JCheckBox(RefactoringBundle.message("inline.parameter.replace.with.local.checkbox"));
    panel.add(myCreateLocalCheckbox, BorderLayout.SOUTH);
    return panel;
  }

  public boolean isCreateLocal() {
    return myCreateLocalCheckbox.isSelected();
  }

  public boolean showDialog() {
      show();
      return isOK();
  }

}
