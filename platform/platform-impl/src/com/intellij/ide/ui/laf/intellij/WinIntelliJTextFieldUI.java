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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseListener;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextFieldUI extends TextFieldWithPopupHandlerUI {
  public static final String HOVER_PROPERTY = "JTextField.hover";

  private MouseListener hoverListener;

  public WinIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJTextFieldUI((JTextField)c);
  }

  @Override
  public void installListeners() {
    super.installListeners();
    hoverListener = new DarculaUIUtil.MouseHoverPropertyTrigger(getComponent(), HOVER_PROPERTY);
    getComponent().addMouseListener(hoverListener);
  }

  @Override
  public void uninstallListeners() {
    super.uninstallListeners();
    if (hoverListener != null) {
      getComponent().removeMouseListener(hoverListener);
    }
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextComponent c = getComponent();
    if (UIUtil.getParentOfType(JComboBox.class, c) != null ||
        UIUtil.getParentOfType(JSpinner.class, c) != null) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Container parent = c.getParent();
      if (c.isOpaque() && parent != null) {
        g2.setColor(parent.getBackground());
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
      }

      paintTextFieldBackground(c, g2);
    } finally {
      g2.dispose();
    }
  }

  static void paintTextFieldBackground(JComponent c, Graphics2D g2) {
    g2.setColor(c.isEnabled() ? c.getBackground() : UIManager.getColor("Button.background"));

    if (!c.isEnabled()) {
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
    }

    Rectangle r = new Rectangle(c.getSize());
    JBInsets.removeFrom(r, JBUI.insets(2));
    adjustInWrapperRect(r, c);

    g2.fill(r);
  }

  static void adjustInWrapperRect(Rectangle r, Component c) {
    if (UIUtil.getParentOfType(Wrapper.class, c) != null && isSearchFieldWithHistoryPopup(c)) {
      int delta = c.getHeight() - c.getPreferredSize().height;
      if (delta > 0) {
        delta -= delta % 2 == 0 ? 0 : 1;
        JBInsets.removeFrom(r, JBUI.insets(delta / 2, 0));
      }
    }
  }

  @Override
  protected int getMinimumHeight() {
    return JBUI.scale(DarculaEditorTextFieldBorder.isComboBoxEditor(getComponent()) ? 18 : 24);
  }

  @Override
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    Icon icon = UIManager.getIcon(clickable ? "TextField.darcula.searchWithHistory.icon" : "TextField.darcula.search.icon");
    if (icon != null && clickable) {
      return new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
          icon.paintIcon(c, g, x, y + JBUI.scale(1));
        }

        @Override
        public int getIconWidth() {
          return icon.getIconWidth() + JBUI.scale(4);
        }

        @Override
        public int getIconHeight() {
          return icon.getIconHeight();
        }
      };
    }
    return icon != null ? icon : IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/search.png", DarculaTextFieldUI.class, true);
  }

  @Override
  protected int getSearchIconGap() {
    return 0;
  }

  @Override
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    if (!clickable) return null;
    Icon icon = UIManager.getIcon("TextField.darcula.clear.icon");
    return icon != null ? icon : IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/clear.png", DarculaTextFieldUI.class, true);
  }

  @Override
  protected int getClearIconGap() {
    return JBUI.scale(3);
  }
}
