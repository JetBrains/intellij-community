/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.ui;

import javax.swing.*;

public class Warning {
  private final String myWarning;
  private final JComponent myComponent;

  public Warning(String warning, JComponent component) {
    myWarning = warning;
    myComponent = component;
  }


  public String toString() {
    return getWarning();
  }

  public String getWarning() {
    return myWarning;
  }

  public JComponent getComponent() {
    return myComponent;
  }

}