package com.intellij.openapi.module.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class RemoveInvalidElementsDialog extends DialogWrapper {
  private JPanel myContentPanel;
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;
  private final Map<JCheckBox, ConfigurationErrorDescription> myCheckboxes = new HashMap<JCheckBox, ConfigurationErrorDescription>();

  private RemoveInvalidElementsDialog(final String title, String invalidElements, final Project project, List<ConfigurationErrorDescription> errors) {
    super(project, true);
    setTitle(title);
    myDescriptionLabel.setText(ProjectBundle.message("label.text.0.cannot.be.loaded", invalidElements));
    myContentPanel.setLayout(new VerticalFlowLayout());
    for (ConfigurationErrorDescription error : errors) {
      JCheckBox checkBox = new JCheckBox(error.getElementName() + ".");
      checkBox.setSelected(true);
      myCheckboxes.put(checkBox, error);
      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.anchor = GridBagConstraints.NORTHWEST;
      constraints.ipadx = 5;
      panel.add(checkBox, constraints);
      constraints.anchor = GridBagConstraints.NORTHWEST;
      constraints.insets.top = 5;
      panel.add(new JLabel("<html><body>" + StringUtil.replace(error.getDescription(), "\n", "<br>") + "</body></html>"), constraints);
      constraints.weightx = 1;
      panel.add(new JPanel(), constraints);
      myContentPanel.add(panel);
    }
    init();
    setOKButtonText(ProjectBundle.message("button.text.remove.selected"));
    setCancelButtonText(ProjectBundle.message("button.text.keep.all"));
  }


  public static void showDialog(@NotNull Project project, @NotNull String title, @NotNull String invalidElements,
                                @NotNull List<ConfigurationErrorDescription> errors) {
    if (errors.isEmpty()) {
      return;
    }
    if (errors.size() == 1) {
      ConfigurationErrorDescription error = errors.get(0);
      String message = error.getDescription() + "\n" + error.getRemoveConfirmationMessage();
      String[] options = {CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()};
      final int answer = Messages.showDialog(project, message, title, options, 1, Messages.getErrorIcon());
      if (answer == 0) {
        error.removeInvalidElement();
      }
      return;
    }

    RemoveInvalidElementsDialog dialog = new RemoveInvalidElementsDialog(title, invalidElements, project, errors);
    dialog.show();
    if (dialog.isOK()) {
      for (ConfigurationErrorDescription errorDescription : dialog.getSelectedItems()) {
        errorDescription.removeInvalidElement();
      }
    }
  }

  private List<ConfigurationErrorDescription> getSelectedItems() {
    List<ConfigurationErrorDescription> items = new ArrayList<ConfigurationErrorDescription>();
    for (Map.Entry<JCheckBox, ConfigurationErrorDescription> entry : myCheckboxes.entrySet()) {
      if (entry.getKey().isSelected()) {
        items.add(entry.getValue());
      }
    }
    return items;
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
