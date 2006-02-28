/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author peter
 */
public class BooleanControl extends com.intellij.util.xml.ui.BaseControl<JCheckBox, Boolean> {
  private boolean myUndefined;

  public BooleanControl(final com.intellij.util.xml.ui.DomWrapper<Boolean> domWrapper) {
    super(domWrapper);
  }

  protected JCheckBox createMainComponent(JCheckBox boundComponent) {
    JCheckBox checkBox = boundComponent == null ? new JCheckBox() : boundComponent;

    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myUndefined = false;
        commit();
      }
    });
    return checkBox;
  }

  protected Boolean getValue(final JCheckBox component) {
    return myUndefined ? null : component.isSelected();
  }

  protected void setValue(final JCheckBox component, final Boolean value) {
    myUndefined = value == null;
    component.setSelected(value != null && value);
  }

}
