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
public class BooleanControl extends BaseControl<JCheckBox, Boolean> {
  private boolean myUndefined;

  public BooleanControl(final DomWrapper<Boolean> domWrapper) {
    super(domWrapper);
  }

  protected JCheckBox createMainComponent(JCheckBox boundComponent) {
    JCheckBox checkBox = boundComponent == null ? new JCheckBox() : boundComponent;

    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myUndefined = false;
        commit();
        reset();
      }
    });
    return checkBox;
  }

  protected Boolean getValue() {
    return myUndefined ? null : getComponent().isSelected();
  }

  protected void setValue(final Boolean value) {
    myUndefined = value == null;
    getComponent().setSelected(value != null && value);
  }

}
