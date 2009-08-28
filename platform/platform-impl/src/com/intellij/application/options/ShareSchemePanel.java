package com.intellij.application.options;

import javax.swing.*;


public class ShareSchemePanel {
  private JTextField mySharedSchemeName;
  private JTextArea mySharedSchemeDescription;
  private JPanel myPanel;

  public String getName(){
    return mySharedSchemeName.getText();
  }

  public String getDescription(){
    return mySharedSchemeDescription.getText();
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void setName(final String name) {
    mySharedSchemeName.setText(name);
  }
}
