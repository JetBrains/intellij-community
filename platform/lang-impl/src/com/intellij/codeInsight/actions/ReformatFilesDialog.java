package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class ReformatFilesDialog extends DialogWrapper {
  private JPanel myPanel;
  private JCheckBox myOptimizeImports;

  public ReformatFilesDialog(Project project) {
    super(project, true);
    setTitle(CodeInsightBundle.message("dialog.reformat.files.title"));
    myOptimizeImports.setSelected(isOptmizeImportsOptionOn());
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public boolean optimizeImports(){
    return myOptimizeImports.isSelected();
  }

  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(LayoutCodeDialog.OPTIMIZE_IMPORTS_KEY, Boolean.toString(myOptimizeImports.isSelected()));
    super.doOKAction();
  }

  private boolean isOptmizeImportsOptionOn() {
    return Boolean.valueOf(PropertiesComponent.getInstance().getValue(LayoutCodeDialog.OPTIMIZE_IMPORTS_KEY));
  }

}
