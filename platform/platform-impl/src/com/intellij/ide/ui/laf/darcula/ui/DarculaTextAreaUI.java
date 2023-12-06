// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.*;
import java.awt.*;
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
    return new TextFieldWithPopupHandlerUI.MouseDragAwareCaret();
  }

  @Override
  protected void paintSafely(Graphics g) {
    if (SystemInfo.isMacOSCatalina) {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }
    super.paintSafely(g);
  }
}
