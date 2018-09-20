// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

public class DarculaMenuSeparatorUI extends DarculaSeparatorUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c )
  {
    return new DarculaMenuSeparatorUI();
  }

  @Override
  protected String getColorResourceName() {
    return "Menu.separatorColor";
  }
}
