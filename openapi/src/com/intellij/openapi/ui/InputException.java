/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import javax.swing.*;

/**
 * author: lesya
 */
public class InputException extends RuntimeException{
  private final String myMessage;
  private final JComponent myComponent;

  public InputException(String message, JComponent component) {
    myMessage = message;
    myComponent = component;
  }

  public void show(){
    Messages.showMessageDialog(myMessage, "Input Error", Messages.getErrorIcon());
    myComponent.requestFocus();
  }
}
