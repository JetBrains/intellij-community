// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicRootPaneUI;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRootPaneUI extends BasicRootPaneUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent comp) {
    return createDefaultWindowsRootPaneUI();
  }

  private static ComponentUI createDefaultWindowsRootPaneUI() {
    try {
      return (ComponentUI)Class.forName("com.sun.java.swing.plaf.windows.WindowsRootPaneUI").newInstance();
    }
    catch (Exception e) {
      return new BasicRootPaneUI();
    }
  }
}