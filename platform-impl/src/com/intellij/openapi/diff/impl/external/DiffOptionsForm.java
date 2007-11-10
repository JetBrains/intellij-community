package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.StringProperty;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DiffOptionsForm {
  private JComponent myPanel;
  // Garbage
  private JCheckBox myEnableFolders;
  private JCheckBox myEnableFiles;
  private TextFieldWithBrowseButton myFoldersTool;
  private TextFieldWithBrowseButton myFilesTool;

  private final ToolPath[] myTools = new ToolPath[2];

  public DiffOptionsForm() {
    myTools[0] = new ToolPath(myEnableFolders, myFoldersTool,
                              DiffManagerImpl.FOLDERS_TOOL, DiffManagerImpl.ENABLE_FOLDERS);
    myTools[1] = new ToolPath(myEnableFiles, myFilesTool, DiffManagerImpl.FILES_TOOL,
                              DiffManagerImpl.ENABLE_FILES);
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    for (ToolPath tool : myTools) {
      if (tool.isModifier()) return true;
    }
    return false;
  }

  public void apply() {
    for (ToolPath tool : myTools) {
      tool.apply();
    }
  }

  public void reset() {
    for (ToolPath tool : myTools) {
      tool.reset();
    }
  }

  private static class ToolPath {
    private final JCheckBox myCheckBox;
    private final TextFieldWithBrowseButton myTextField;
    private final StringProperty myPathProperty;
    private final BooleanProperty myEnabledProperty;

    public ToolPath(JCheckBox checkBox, TextFieldWithBrowseButton textField,
                    StringProperty pathProperty, BooleanProperty enabledProperty) {
      myCheckBox = checkBox;
      myTextField = textField;
      myPathProperty = pathProperty;
      myEnabledProperty = enabledProperty;
      final ButtonModel model = myCheckBox.getModel();
      model.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateEnabledEffect();
        }
      });
      myTextField.addBrowseFolderListener(DiffBundle.message("select.external.diff.program.dialog.title"), null, null,
                                          FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                          TextComponentAccessor.TEXT_FIELD_SELECTED_TEXT);
    }

    private void updateEnabledEffect() {
      UIUtil.setEnabled(myTextField, isEnabled(), true);
    }

    public boolean isModifier() {
      AbstractProperty.AbstractPropertyContainer properties = getProperties();
      return !myTextField.getText().equals(myPathProperty.get(properties)) ||
             isEnabled() != myEnabledProperty.value(properties);
    }

    private boolean isEnabled() {
      return myCheckBox.getModel().isSelected();
    }

    private static AbstractProperty.AbstractPropertyContainer getProperties() {
      return DiffManagerImpl.getInstanceEx().getProperties();
    }

    public void apply() {
      myPathProperty.set(getProperties(), myTextField.getText());
      myEnabledProperty.primSet(getProperties(), isEnabled());
    }

    public void reset() {
      myTextField.setText(myPathProperty.get(getProperties()));
      myCheckBox.getModel().setSelected(myEnabledProperty.value(getProperties()));
      updateEnabledEffect();
    }
  }
}
