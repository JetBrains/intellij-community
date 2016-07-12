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

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import static javax.swing.SwingConstants.WEST;

public class DarculaTextAreaUI extends BasicTextAreaUI{
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaTextAreaUI();
  }

  @Override
  public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b, int direction, Position.Bias[] biasRet)
    throws BadLocationException {
    if (direction == WEST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionEnd() == pos) {
      return t.getSelectionStart();
    }
    return super.getNextVisualPositionFrom(t, pos, b, direction, biasRet);
  }
}
