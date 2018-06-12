// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.util.ui.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.COMPONENT_ARC;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.MINIMUM_HEIGHT;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextFieldUI extends TextFieldWithPopupHandlerUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTextFieldUI();
  }

  @Override
  protected int getMinimumHeight(int textHeight) {
    Insets i = getComponent().getInsets();
    JComponent c = getComponent();
    return DarculaEditorTextFieldBorder.isComboBoxEditor(c) || UIUtil.getParentOfType(JSpinner.class, c) != null ?
              textHeight : MINIMUM_HEIGHT.get() + i.top + i.bottom;
  }

  @Override
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    return LafIconLookup.getIcon(clickable ? "searchWithHistory" : "search");
  }

  @Override
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    return !clickable ? null : LafIconLookup.getIcon("clear");
  }

  @Override
  protected int getClearIconPreferredSpace() {
    return super.getClearIconPreferredSpace() - getClearIconGap();
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextComponent component = getComponent();
    if (component != null) {
      Container parent = component.getParent();
      if (parent != null && component.isOpaque()) {
        g.setColor(parent.getBackground());
        g.fillRect(0, 0, component.getWidth(), component.getHeight());
      }

      if (component.getBorder() instanceof DarculaTextBorder) {
        paintDarculaBackground(g, component);
      } else if (component.isOpaque()) {
        super.paintBackground(g);
      }
    }
  }

  protected void paintDarculaBackground(Graphics g, JTextComponent component) {
    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(component.getSize());
    JBInsets.removeFrom(r, DarculaUIUtil.paddings());

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      g2.translate(r.x, r.y);

      if (component.isEnabled() && component.isEditable()) {
        float arc = isSearchField(component) ? COMPONENT_ARC.getFloat() : 0.0f;
        float bw = bw();

        g2.setColor(component.getBackground());
        g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  protected Insets getDefaultMargins() {
    return JBUI.insets(2, 5);
  }

  protected float bw() {
    return DarculaUIUtil.BW.getFloat();
  }
}
