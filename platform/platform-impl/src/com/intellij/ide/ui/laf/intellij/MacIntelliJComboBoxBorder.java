// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.paintOutlineBorder;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJComboBoxBorder extends MacIntelliJTextBorder {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!(c instanceof JComponent)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      Shape clip = g2.getClip();
      Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
      area.subtract(getButtonBounds(c));
      area.intersect(new Area(clip));
      g2.setClip(area);

      float arc = isRound(c) ? ARC.getFloat() : 0;
      float bw = BW.getFloat();
      float lw = LW(g2);

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Double(bw, bw, width - bw * 2, height - bw * 2, arc, arc), false);
      border.append(new RoundRectangle2D.Double(bw + lw, bw + lw, width - (bw + lw) * 2, height - (bw + lw) * 2, arc - lw, arc - lw), false);

      g2.setColor(Gray.xBC);
      g2.fill(border);

      g2.setClip(clip); // Reset clip

      clipForBorder(c, g2, width, height);

      Object op = ((JComponent)c).getClientProperty("JComponent.outline");
      boolean focused = isFocused(c);
      if (op != null) {
        paintOutlineBorder(g2, width, height, arc, isSymmetric(), focused, DarculaUIUtil.Outline.valueOf(op.toString()));
      } else if (focused) {
        paintOutlineBorder(g2, width, height, arc, isSymmetric(), true, DarculaUIUtil.Outline.focus);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  @Override
  protected boolean isFocused(Component c) {
    if (c instanceof JComboBox) {
      JComboBox comboBox = (JComboBox)c;

      if (!comboBox.isEnabled()) {
        return false;
      }

      if (comboBox.isEditable()) {
        ComboBoxEditor ed = comboBox.getEditor();
        Component editorComponent = ed.getEditorComponent();
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        return focused != null && editorComponent != null && SwingUtilities.isDescendingFrom(focused, editorComponent);
      } else {
        return comboBox.hasFocus();
      }
    }
    return false;
  }

  Area getButtonBounds(Component c) {
    Rectangle bounds = null;
    if (c instanceof JComboBox && ((JComboBox)c).getUI() instanceof MacIntelliJComboBoxUI) {
      MacIntelliJComboBoxUI ui = (MacIntelliJComboBoxUI)((JComboBox)c).getUI();
      bounds = ui.getArrowButtonBounds();
    }
    return bounds != null ? new Area(bounds) : new Area();
  }

  boolean isRound(Component c) {
    return c instanceof JComboBox && !((JComboBox)c).isEditable();
  }

  @Override
  protected void clipForBorder(Component c, Graphics2D g2, int width, int height) {
    float lw = LW(g2);
    float bw = BW.getFloat();
    Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
    Shape innerShape = isRound(c) ?
           new RoundRectangle2D.Float(bw + lw, bw + lw, width - (bw + lw) * 2, height - (bw + lw) * 2, bw + lw, bw + lw) :
           new Rectangle2D.Float(bw + lw, bw + lw, width - (bw + lw) * 2, height - (bw + lw) * 2);

    area.subtract(new Area(innerShape));
    area.add(getButtonBounds(c));

    Area clip = new Area(g2.getClip());
    area.intersect(clip);
    g2.setClip(area);
  }

  @Override
  protected boolean isSymmetric() {
    return false;
  }
}
