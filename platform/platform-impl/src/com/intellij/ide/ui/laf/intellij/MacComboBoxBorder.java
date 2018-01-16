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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacComboBoxBorder extends MacIntelliJTextBorder {

  private static final int VALUE_OFFSET = 5;

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

      float arc = isRound(c) ? JBUI.scale(6f) : 0;
      Insets i = ((JComponent)c).getInsets();

      if (c instanceof JComboBox) {
        JComboBox comboBox = (JComboBox)c;
        ComboBoxEditor cbe = comboBox.getEditor();
        Color background = comboBox.isEditable() ? cbe.getEditorComponent().getBackground() :
                           UIManager.getColor(comboBox.isEnabled() ? "ComboBox.background" : "ComboBox.disabledBackground");

        g2.setColor(background);
        if (comboBox.isEditable()) {
            Shape shape = new Rectangle2D.Double(i.left, i.top,
                                   width - (i.left + i.right),
                                   height - (i.top + i.bottom));
            g2.fill(shape);
        } else {
          int vo = JBUI.scale(VALUE_OFFSET);
          Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
          path.moveTo(i.left + vo, i.top);
          path.lineTo(i.left + vo, height - i.bottom);
          path.lineTo(i.left + arc, height - i.bottom);
          path.quadTo(i.left, height - i.bottom, i.left, height - arc - i.bottom);
          path.lineTo(i.left, arc + i.top);
          path.quadTo(i.left, i.top, arc + i.left, i.top);
          path.closePath();
          g2.fill(path);
        }
      }

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      float lw = JBUI.scale(UIUtil.isRetina(g2) ? 0.5f : 1.0f);
      border.append(new RoundRectangle2D.Double(JBUI.scale(3), JBUI.scale(3),
                                           width - JBUI.scale(3)*2,
                                           height - JBUI.scale(3)*2,
                                                arc, arc), false);
      float innerArc = JBUI.scale(arc > 0 ? arc - lw : 0.0f);
      border.append(new RoundRectangle2D.Double(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                           width - (JBUI.scale(3) + lw) * 2,
                                           height - (JBUI.scale(3) + lw) * 2,
                                                innerArc, innerArc), false);
      g2.setColor(Gray.xBC);
      g2.fill(border);

      g2.setClip(clip); // Reset clip
      paint(c, g2, width, height, arc);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
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
    float lw = JBUI.scale(UIUtil.isRetina(g2) ? 0.5f : 1.0f);
    Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
    Shape innerShape = isRound(c) ?
           new RoundRectangle2D.Float(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                       width - (JBUI.scale(3) + lw) * 2,
                                       height - (JBUI.scale(3) + lw) * 2,
                                       JBUI.scale(3) + lw, JBUI.scale(3) + lw) :
           new Rectangle2D.Float(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                  width - (JBUI.scale(3) + lw) * 2,
                                  height - (JBUI.scale(3) + lw) * 2);

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
