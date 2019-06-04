// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isCompact;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isTableCellEditor;
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
  protected int getMinimumHeight(int textHeight) {
    Insets i = getComponent().getInsets();
    Component c = getComponent();
    return DarculaEditorTextFieldBorder.isComboBoxEditor(c) ||
           ComponentUtil.getParentOfType((Class<? extends JSpinner>)JSpinner.class, c) != null ||
           isCompact(c) ?
           textHeight : MINIMUM_HEIGHT.get() + i.top + i.bottom;
  }

  @Override
  protected Insets getDefaultMargins() {
    Component c = getComponent();
    return isCompact(c) || isTableCellEditor(c) ? JBInsets.create(0, 3) : JBInsets.create(1, 6);
  }

  @Override
  protected float bw() {
    return BW.getFloat();
  }
}
