package com.intellij.lang.customFolding;

import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Rustam Vishnyakov
 */
public class CustomFoldingSettingsPanel {
  private JPanel mySettingsPanel;
  private JTextField myFoldingStartField;
  private JTextField myFoldingEndField;
  private JBCheckBox myEnabledBox;
  private JTextField myCollapsedStateField;
  private JRadioButton myVisualStudioRadioButton;
  private JRadioButton myNetBeansRadioButton;
  private JPanel myPredefinedPatternsPanel;

  public CustomFoldingSettingsPanel() {
    myEnabledBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean isEnabled = myEnabledBox.isSelected();
        setFieldsEnabled(isEnabled);
      }
    });
    ButtonGroup predefinedSettingsGroup = new ButtonGroup();
    predefinedSettingsGroup.add(myNetBeansRadioButton);
    predefinedSettingsGroup.add(myVisualStudioRadioButton);
    myVisualStudioRadioButton.setSelected(false);
    myNetBeansRadioButton.setSelected(false);
    
    myNetBeansRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myFoldingStartField.setText(".*<editor-fold .*desc=\"(.*)\".*$");
        myFoldingEndField.setText(".*</editor-fold>");
        myCollapsedStateField.setText("defaultstate=\"collapsed\"");
      }
    });

    myVisualStudioRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myFoldingStartField.setText(".*region (.*)$");
        myFoldingEndField.setText(".*endregion");
        myCollapsedStateField.setText("");
      }
    });
  }

  public JComponent getComponent() {
    return mySettingsPanel;
  }
  
  public String getStartPattern() {
    return myFoldingStartField.getText();
  }
  
  public String getEndPattern() {
    return myFoldingEndField.getText();
  }
  
  public void setStartPattern(String startPattern) {
    myFoldingStartField.setText(startPattern);
  }
  
  public void setEndPattern(String endPattern) {
    myFoldingEndField.setText(endPattern);
  }
  
  public void setEnabled(boolean enabled) {
    myEnabledBox.setSelected(enabled);
    setFieldsEnabled(enabled);
  }
  
  public boolean isEnabled() {
    return myEnabledBox.isSelected();
  }
  
  public void setCollapsedStatePattern(String pattern) {
    myCollapsedStateField.setText(pattern);
  }
  
  public String getCollapsedStatePattern() {
    return myCollapsedStateField.getText();
  }

  private void setFieldsEnabled(boolean enabled) {
    myFoldingStartField.setEnabled(enabled);
    myFoldingEndField.setEnabled(enabled);
    myCollapsedStateField.setEnabled(enabled);
    myPredefinedPatternsPanel.setEnabled(enabled);
    myNetBeansRadioButton.setEnabled(enabled);
    myVisualStudioRadioButton.setEnabled(enabled);
  }
}
