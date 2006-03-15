/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.javaee.J2EEBundle;
import com.intellij.util.Icons;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author peter
 */
public class BigStringComponent extends TextFieldWithBrowseButton {

  public BigStringComponent() {
    this(true);
    setButtonIcon(Icons.OPEN_EDIT_DIALOG_ICON);
  }

  public BigStringComponent(boolean hasBorder) {
    super();
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(getTextField(), J2EEBundle.message("column.name.description"), "DescriptionDialogEditor");
      }
    });
    if (!hasBorder) {
      getTextField().setBorder(null);
    }
  }
}
