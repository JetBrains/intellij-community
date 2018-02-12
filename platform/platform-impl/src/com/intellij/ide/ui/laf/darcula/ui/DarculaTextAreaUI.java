/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.*;
import java.awt.event.KeyEvent;

public class DarculaTextAreaUI extends BasicTextAreaUI{
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
    return Registry.is("ide.text.mouse.selection.new") ? new TextFieldWithPopupHandlerUI.MyCaret(getComponent()) : super.createCaret();
  }
}
