// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.WindowManagerImpl;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicRootPaneUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

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
    } catch (Exception e) {
      return new BasicRootPaneUI();
    }
  }
}