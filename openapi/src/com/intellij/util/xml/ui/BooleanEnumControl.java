/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.NamedEnumUtil;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author peter
 */
public class BooleanEnumControl extends BaseControl<JCheckBox, String> {
  private boolean myUndefined;
  private final String mySelectedValue;
  private final String myUnselectedValue;

  public BooleanEnumControl(final DomWrapper<String> domWrapper, String selectedValue, String unselectedValue) {
    super(domWrapper);
    mySelectedValue = selectedValue;
    myUnselectedValue = unselectedValue;
  }

  public BooleanEnumControl(final DomWrapper<String> domWrapper, Class<? extends Enum> enumClass, boolean invertedOrder) {
    this(domWrapper, NamedEnumUtil.getEnumValueByElement(enumClass.getEnumConstants()[invertedOrder ? 0 : 1]), NamedEnumUtil.getEnumValueByElement(enumClass.getEnumConstants()[invertedOrder ? 1 : 0]));
    assert enumClass.getEnumConstants().length == 2 : enumClass;
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

  protected String getValue() {
    return myUndefined ? null : (getComponent().isSelected() ? mySelectedValue : myUnselectedValue);
  }

  protected void setValue(final String value) {
    myUndefined = value == null;
    getComponent().setSelected(mySelectedValue.equals(value));
  }

}
