// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

import static com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.BW;
import static com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.MINIMUM_HEIGHT;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public class MacIntelliJTextFieldUI extends DarculaTextFieldUI {
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
  protected int getMinimumHeight(int textHeight) {
    Insets i = getComponent().getInsets();
    Component c = getComponent();
    return DarculaEditorTextFieldBorder.isComboBoxEditor(c) || UIUtil.getParentOfType(JSpinner.class, c) != null ?
           textHeight : MINIMUM_HEIGHT.get() + i.top + i.bottom;
  }

  @Override
  protected Insets getDefaultMargins() {
    return JBUI.insets(1, 5);
  }

  @Override
  protected float bw() {
    return BW.getFloat();
  }
}
