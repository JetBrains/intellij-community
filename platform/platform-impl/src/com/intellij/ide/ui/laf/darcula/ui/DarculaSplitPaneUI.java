// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalSplitPaneUI;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class DarculaSplitPaneUI extends MetalSplitPaneUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaSplitPaneUI();
  }
}
