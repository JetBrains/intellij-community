// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public class MacIntelliJTextFieldUI extends DarculaTextFieldUI {
  public static final int BW = 3;
  public static final int MACOS_LIGHT_INPUT_HEIGHT_TOTAL = MACOS_LIGHT_INPUT_HEIGHT + (BW * 2);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(final JComponent c) {
    return new MacIntelliJTextFieldUI();
  }

  @Override
  protected void updateIconsLayout(Rectangle bounds) {
    super.updateIconsLayout(bounds);
    JTextComponent component = getComponent();
    if (component == null || component.hasFocus()) return;
    IconHolder clear = icons.get("clear");
    if (clear == null || clear.icon != null) return;
    IconHolder search = icons.get("search");
    if (search == null || search.icon == null || search.isClickable()) return;
    search.bounds.x = bounds.x + (bounds.width - search.bounds.width) / 2;
  }

  @Override
  protected int getMinimumHeightForTextField() {
    return DarculaTextFieldUI.MACOS_LIGHT_INPUT_HEIGHT;
  }

  @Override
  protected int getMinimumHeight() {
    JTextComponent component = getComponent();
    if (JBUI.isUseCorrectInputHeightOnMacOS(component)) {
      return super.getMinimumHeight();
    }
    return DarculaEditorTextFieldBorder.isComboBoxEditor(component) ? JBUI.scale(18) : JBUI.scale(26);
  }

  @Override
  protected float bw() {
    return JBUI.scale(BW);
  }
}
