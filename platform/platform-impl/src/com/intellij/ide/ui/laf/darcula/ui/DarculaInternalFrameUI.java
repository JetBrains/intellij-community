// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import javax.swing.plaf.basic.BasicInternalFrameUI;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class DarculaInternalFrameUI extends BasicInternalFrameUI {
  public DarculaInternalFrameUI(JInternalFrame b) {
    super(b);
  }


  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaInternalFrameUI((JInternalFrame)c);
  }

  @Override
  protected JComponent createNorthPane(JInternalFrame w) {
    this.titlePane = new BasicInternalFrameTitlePane(w) {

    };
    return this.titlePane;
  }
}
