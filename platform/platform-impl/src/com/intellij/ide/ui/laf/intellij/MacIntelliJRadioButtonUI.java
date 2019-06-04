// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJRadioButtonUI extends DarculaRadioButtonUI {
  private static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(22));

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJRadioButtonUI();
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }

  @Override
  protected int textIconGap() {
    return JBUIScale.scale(3);
  }
}
