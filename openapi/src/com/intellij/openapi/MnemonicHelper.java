/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi;

import com.intellij.ui.ComponentTreeWatcher;
import com.intellij.util.ui.DialogUtil;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Aug 29, 2005
 * Time: 6:03:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class MnemonicHelper extends ComponentTreeWatcher {
  public static final PropertyChangeListener TEXT_LISTENER = new PropertyChangeListener() {
   public void propertyChange(PropertyChangeEvent evt) {
     final Object source = evt.getSource();
     if (source instanceof AbstractButton) {
       DialogUtil.registerMnemonic(((AbstractButton)source));
     } else if (source instanceof JLabel) {
       DialogUtil.registerMnemonic(((JLabel)source), null);
     }
   }
  };
  @NonNls public static final String TEXT_CHANGED_PROPERTY = "text";

  public MnemonicHelper() {
    super(new Class[0]);
  }

  protected void processComponent(Component parentComponent) {
    if (parentComponent instanceof AbstractButton) {
      final AbstractButton abstractButton = ((AbstractButton)parentComponent);
      abstractButton.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      if (abstractButton.getMnemonic() < 0){
        DialogUtil.registerMnemonic(abstractButton);
      }
    } else if (parentComponent instanceof JLabel) {
      final JLabel jLabel = ((JLabel)parentComponent);
      jLabel.addPropertyChangeListener(TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      if (jLabel.getDisplayedMnemonicIndex() < 0) {
        DialogUtil.registerMnemonic(jLabel, null);
      }
    }
  }

  protected void unprocessComponent(Component component) {
  }
}
