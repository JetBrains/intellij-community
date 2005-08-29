/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi;

import com.intellij.ui.ComponentTreeWatcher;
import com.intellij.util.ui.DialogUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 29, 2005
 * Time: 6:03:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class MnemonicHelper extends ComponentTreeWatcher {
  public MnemonicHelper() {
    super(new Class[0]);
  }

  protected void processComponent(Component parentComponent) {
    if (parentComponent instanceof AbstractButton) {
      final AbstractButton abstractButton = ((AbstractButton)parentComponent);
      if (abstractButton.getMnemonic() <= 0){
        DialogUtil.registerMnemonic(abstractButton);
      }
    } else if (parentComponent instanceof JLabel) {
      final JLabel jLabel = ((JLabel)parentComponent);
      if (jLabel.getDisplayedMnemonicIndex() < 0) {
        DialogUtil.registerMnemonic(jLabel, null);
      }
    }
  }

  protected void unprocessComponent(Component component) {
  }
}
