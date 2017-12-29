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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaEditorTextFieldBorder extends DarculaTextBorder {
  public DarculaEditorTextFieldBorder() {
    this(null, null);
  }

  public DarculaEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
    if (editorTextField != null && editor != null) {
      editor.addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(Editor editor) {
          editorTextField.repaint();
        }

        @Override
        public void focusLost(Editor editor) {
          editorTextField.repaint();
        }
      });
    }
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (isComboBoxEditor(c)) {
      g.setColor(c.getBackground());
      g.fillRect(x, y, width, height);
      return;
    }

    EditorTextField editorTextField = UIUtil.getParentOfType(EditorTextField.class, c);
    if (editorTextField == null) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      if (c.isOpaque()) {
        g2.setColor(UIUtil.getPanelBackground());
        g2.fillRect(x, y, width, height);
      }

      Rectangle r = new Rectangle(x, y, width, height);
      //JBInsets.removeFrom(r, JBUI.insets(1, 0));
      g2.translate(r.x, r.y);

      float lw = lw(g2);
      float bw = bw();

      Shape outer = new Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2);
      g2.setColor(c.getBackground());
      g2.fill(outer);

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(outer, false);
      border.append(new Rectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2), false);

      g2.setColor(getOutlineColor(c.isEnabled()));
      g2.fill(border);

      boolean hasFocus = editorTextField.getFocusTarget().hasFocus();
      Object op = editorTextField.getClientProperty("JComponent.outline");
      if (op != null) {
        g.translate(x, y);
        DarculaUIUtil.paintOutlineBorder(g2, r.width, r.height, 0, true, hasFocus,
                                         DarculaUIUtil.Outline.valueOf(op.toString()));
      } else if (editorTextField.isEnabled() && editorTextField.isVisible() && hasFocus) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, 0, true);
      }

    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return isComboBoxEditor(c) ? JBUI.insets(2, 3).asUIResource() : JBUI.insets(4, 8).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  public static boolean isComboBoxEditor(Component c) {
    return UIUtil.getParentOfType(JComboBox.class, c) != null;
  }
}
