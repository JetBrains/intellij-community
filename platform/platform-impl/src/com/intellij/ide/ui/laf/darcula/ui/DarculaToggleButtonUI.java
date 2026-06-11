// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import org.jetbrains.annotations.ApiStatus;

@SuppressWarnings("unused")
@ApiStatus.Internal
public final class DarculaToggleButtonUI extends DarculaButtonUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new DarculaToggleButtonUI();
  }
}
