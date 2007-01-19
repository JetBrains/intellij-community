package com.intellij.codeInspection.export;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.OptionGroup;

import javax.swing.*;

// TODO copy-paste result of com.intellij.codeEditor.printing.ExportToHTMLDialog
public class ExportToHTMLDialog extends DialogWrapper{
  private JCheckBox myCbOpenInBrowser;
  private final Project myProject;
  private TextFieldWithBrowseButton myTargetDirectoryField;
  private boolean myCanBeOpenInBrowser;

  public ExportToHTMLDialog(Project project, final boolean canBeOpenInBrowser) {
    super(project, true);
    myProject = project;
    myCanBeOpenInBrowser = canBeOpenInBrowser;
    setOKButtonText(InspectionsBundle.message("inspection.export.save.button"));
    setTitle(InspectionsBundle.message("inspection.export.dialog.title"));
    init();
  }

  protected JComponent createNorthPanel() {
    OptionGroup optionGroup = new OptionGroup();

    myTargetDirectoryField = new TextFieldWithBrowseButton();
    optionGroup.add(com.intellij.codeEditor.printing.ExportToHTMLDialog.assignLabel(myTargetDirectoryField, myProject));

    return optionGroup.createPanel();
  }

  protected JComponent createCenterPanel() {
    if (!myCanBeOpenInBrowser) return null;
    OptionGroup optionGroup = new OptionGroup(InspectionsBundle.message("inspection.export.options.panel.title"));

    myCbOpenInBrowser = new JCheckBox();
    myCbOpenInBrowser.setText(InspectionsBundle.message("inspection.export.open.option"));
    optionGroup.add(myCbOpenInBrowser);

    return optionGroup.createPanel();
  }

  public void reset() {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);
    if (myCanBeOpenInBrowser) {
      myCbOpenInBrowser.setSelected(exportToHTMLSettings.OPEN_IN_BROWSER);
    }
    myTargetDirectoryField.setText(exportToHTMLSettings.OUTPUT_DIRECTORY);
  }

  public void apply() {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);

    if (myCanBeOpenInBrowser) {
      exportToHTMLSettings.OPEN_IN_BROWSER = myCbOpenInBrowser.isSelected();
    }
    exportToHTMLSettings.OUTPUT_DIRECTORY = myTargetDirectoryField.getText();
  }
}