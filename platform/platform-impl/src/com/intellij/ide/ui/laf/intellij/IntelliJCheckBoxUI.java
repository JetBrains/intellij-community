// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

public final class IntelliJCheckBoxUI extends DarculaCheckBoxUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new IntelliJCheckBoxUI();
  }
}
