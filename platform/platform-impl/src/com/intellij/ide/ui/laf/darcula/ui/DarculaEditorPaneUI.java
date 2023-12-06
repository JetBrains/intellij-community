// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicEditorPaneUI;

/**
 * @author Konstantin Bulenkov
 */
public final class DarculaEditorPaneUI extends BasicEditorPaneUI {
  private final JEditorPane myEditorPane;

  public DarculaEditorPaneUI(JComponent comp) {
    myEditorPane = ((JEditorPane)comp);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent comp) {
    return new DarculaEditorPaneUI(comp);
  }
}
