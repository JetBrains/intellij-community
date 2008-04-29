package com.intellij.openapi.module.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class RemoveInvalidElementsDialog<E extends RemoveInvalidElementsDialog.ErrorDescription> extends DialogWrapper {
  private JPanel myContentPanel;
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;
  private Map<JCheckBox, E> myCheckboxes = new HashMap<JCheckBox, E>();

  private RemoveInvalidElementsDialog(final String title, String description, final Project project, Collection<E> errors) {
    super(project, true);
    setTitle(title);
    myDescriptionLabel.setText(description);
    myContentPanel.setLayout(new VerticalFlowLayout());
    for (E error : errors) {
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


  public static <E extends ErrorDescription> List<E> showDialog(@NotNull Project project, @NotNull String title, @NotNull String description,
                                                                final String removeConfirmation, @NotNull List<E> elements) {
    if (elements.size() == 1) {
      E element = elements.get(0);
      String message = element.getDescription() + "\n" + MessageFormat.format(removeConfirmation, element.getElementName());
      String[] options = {CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()};
      final int answer = Messages.showDialog(project, message, title, options, 1, Messages.getErrorIcon());
      return answer == 0 ? new ArrayList<E>(elements) : Collections.<E>emptyList();
    }

    RemoveInvalidElementsDialog<E> dialog = new RemoveInvalidElementsDialog<E>(title, description, project, elements);
    dialog.show();
    if (!dialog.isOK()) {
      return Collections.emptyList();
    }
    return dialog.getSelectedItems();
  }

  private List<E> getSelectedItems() {
    List<E> items = new ArrayList<E>();
    for (Map.Entry<JCheckBox, E> entry : myCheckboxes.entrySet()) {
      if (entry.getKey().isSelected()) {
        items.add(entry.getValue());
      }
    }
    return items;
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public static interface ErrorDescription {
    String getDescription();

    String getElementName();
  }
}
