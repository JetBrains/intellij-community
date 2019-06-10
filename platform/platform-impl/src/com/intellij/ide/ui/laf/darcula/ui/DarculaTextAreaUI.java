// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.*;
import java.awt.event.KeyEvent;

public class DarculaTextAreaUI extends BasicTextAreaUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaTextAreaUI();
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
    if (SystemInfo.isMac) {
      InputMap inputMap = getComponent().getInputMap();
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), DefaultEditorKit.upAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), DefaultEditorKit.downAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), DefaultEditorKit.pageUpAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), DefaultEditorKit.pageDownAction);
    }
  }

  @Override
  public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b, int direction, Position.Bias[] biasRet)
    throws BadLocationException {
    int position = DarculaUIUtil.getPatchedNextVisualPositionFrom(t, pos, direction);
    return position != -1 ? position : super.getNextVisualPositionFrom(t, pos, b, direction, biasRet);
  }

  @Override
  protected Caret createCaret() {
    return Registry.is("ide.text.mouse.selection.new")
           ? new TextFieldWithPopupHandlerUI.MouseDragAwareCaret()
           : new TextFieldWithPopupHandlerUI.MarginAwareCaret();
  }
}
