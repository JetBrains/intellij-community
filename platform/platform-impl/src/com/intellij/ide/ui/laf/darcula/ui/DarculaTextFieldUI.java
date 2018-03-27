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
import com.intellij.ide.ui.laf.intellij.MacIntelliJIconCache;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextFieldUI extends TextFieldWithPopupHandlerUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTextFieldUI();
  }

  @Override
  protected int getMinimumHeight() {
    Insets i = getComponent().getInsets();
    JComponent c = getComponent();
    return DarculaEditorTextFieldBorder.isComboBoxEditor(c) ||
           UIUtil.getParentOfType(JSpinner.class, c) != null ?
            JBUI.scale(18) : JBUI.scale(16) + i.top + i.bottom;
  }

  @Override
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    return MacIntelliJIconCache.getIcon(clickable ? "searchFieldWithHistory" : "search");
  }

  @Override
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    return !clickable ? null : MacIntelliJIconCache.getIcon("searchFieldClear");
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

  @Override
  protected void updatePreferredSize(JComponent c, Dimension size) {
    super.updatePreferredSize(c, size);
    Insets i = c.getInsets();
    size.width += i.left + i.right;
  }

  protected void paintDarculaBackground(Graphics g, JTextComponent component) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      Rectangle r = new Rectangle(component.getWidth(), component.getHeight());
      //JBInsets.removeFrom(r, JBUI.insets(1, 0));
      g2.translate(r.x, r.y);

      float arc = isSearchField(component) ? JBUI.scale(6f) : 0.0f;
      float bw = bw();

      if (component.isEnabled() && component.isEditable()) {
        g2.setColor(component.getBackground());
      }

      g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
    } finally {
      g2.dispose();
    }
  }

  protected float bw() {
    return DarculaUIUtil.bw();
  }
}
