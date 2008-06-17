package com.intellij.application.options;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class ShareSchemeDialog extends DialogWrapper {
  private final ShareSchemePanel myShareSchemePanel;

  public ShareSchemeDialog() {
    super(true);
    myShareSchemePanel = new ShareSchemePanel();
    setTitle("Share Scheme");
    init();
  }

  public void init(Scheme scheme){
    myShareSchemePanel.setName(scheme.getName());
  }

  protected JComponent createCenterPanel() {
    return myShareSchemePanel.getPanel();
  }

  public String getName(){
    return myShareSchemePanel.getName();
  }

  public String getDescription(){
    return myShareSchemePanel.getDescription();
  }

}
