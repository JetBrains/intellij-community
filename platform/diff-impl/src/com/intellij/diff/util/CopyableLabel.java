/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class CopyableLabel extends JTextArea {
  @NotNull private JLabel ELLIPSIS_LABEL = new JLabel("...");

  @NotNull
  public static JComponent create(@NotNull String text) {
    if (text.isEmpty()) text = " ";
    text = text.replace('\r', ' ').replace('\n', ' ');

    if (Registry.is("diff.copyable.title.label")) {
      return new CopyableLabel(text);
    }
    else {
      return new JLabel(text);
    }
  }

  private CopyableLabel(@NotNull String text) {
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        int caretPosition = getCaretPosition();
        setSelectionStart(caretPosition);
        setSelectionEnd(caretPosition);
      }
    });
    setWrapStyleWord(false);
    setFont(UIUtil.getLabelFont());
    setEditable(false);
    setForeground(UIUtil.getLabelForeground());
    setBackground(UIUtil.TRANSPARENT_COLOR);
    setBorder(null);
    setOpaque(false);
    setText(text);
    setCaretPosition(0);
  }

  @Override
  public void paint(Graphics g) {
    Dimension size = getSize();
    boolean paintEllipsis = getPreferredSize().width > size.width;

    if (!paintEllipsis) {
      super.paint(g);
    }
    else {
      Dimension ellipsisSize = ELLIPSIS_LABEL.getPreferredSize();
      int endOffset = size.width - ellipsisSize.width;
      try {
        // do not paint half of the letter
        endOffset = modelToView(viewToModel(new Point(endOffset, 0)) - 1).x;
      }
      catch (BadLocationException ignore) {
      }
      Shape oldClip = g.getClip();
      g.clipRect(0, 0, endOffset, size.height);

      super.paint(g);
      g.setClip(oldClip);

      g.translate(endOffset, 0);
      ELLIPSIS_LABEL.setSize(ellipsisSize);
      ELLIPSIS_LABEL.paint(g);
      g.translate(-endOffset, 0);
    }
  }
}
