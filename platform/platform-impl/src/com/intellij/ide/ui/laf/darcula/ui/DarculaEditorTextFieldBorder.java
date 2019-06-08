// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.VisualPaddingsProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaEditorTextFieldBorder extends DarculaTextBorder implements VisualPaddingsProvider {
  public DarculaEditorTextFieldBorder() {
    this(null, null);
  }

  public DarculaEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
    if (editorTextField != null && editor != null) {
      editor.addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(@NotNull Editor editor) {
          editorTextField.repaint();
        }

        @Override
        public void focusLost(@NotNull Editor editor) {
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

    EditorTextField editorTextField = ComponentUtil.getParentOfType((Class<? extends EditorTextField>)EditorTextField.class, c);
    if (editorTextField == null) return;
    boolean hasFocus = editorTextField.getFocusTarget().hasFocus();

    Rectangle r = new Rectangle(x, y, width, height);

    if (isTableCellEditor(c)) {
      paintCellEditorBorder((Graphics2D)g, c, r, hasFocus);
    }
    else {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        if (c.isOpaque()) {
          g2.setColor(UIUtil.getPanelBackground());
          g2.fill(r);
        }

        JBInsets.removeFrom(r, JBUI.insets(1));
        g2.translate(r.x, r.y);

        float lw = lw(g2);
        float bw = bw();

        Shape outer = new Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2);
        g2.setColor(c.getBackground());
        g2.fill(outer);

        Object op = editorTextField.getClientProperty("JComponent.outline");
        if (editorTextField.isEnabled() && op != null) {
          paintOutlineBorder(g2, r.width, r.height, 0, true, hasFocus, Outline.valueOf(op.toString()));
        }
        else if (editorTextField.isEnabled() && editorTextField.isVisible()) {
          if (hasFocus) {
            paintOutlineBorder(g2, r.width, r.height, 0, true, true, Outline.focus);
          }

          Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
          border.append(outer, false);
          border.append(new Rectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2), false);

          g2.setColor(getOutlineColor(editorTextField.isEnabled(), hasFocus));
          g2.fill(border);
        }
      }
      finally {
        g2.dispose();
      }
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return isTableCellEditor(c) || isCompact(c) || isComboBoxEditor(c) ?
           JBInsets.create(2, 3).asUIResource() : JBInsets.create(6, 8).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  public static boolean isComboBoxEditor(Component c) {
    return ComponentUtil.getParentOfType((Class<? extends JComboBox>)JComboBox.class, c) != null;
  }

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    return JBUI.insets(3);
  }
}
