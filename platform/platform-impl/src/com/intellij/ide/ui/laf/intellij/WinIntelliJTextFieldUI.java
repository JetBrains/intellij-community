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
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
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
public class WinIntelliJTextFieldUI extends DarculaTextFieldUI {
  public static final String HOVER_PROPERTY = "JTextField.hover";

  private MouseListener hoverListener;

  public WinIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

    @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJTextFieldUI((JTextField)c);
  }

  @Override public void installListeners() {
    super.installListeners();
    hoverListener = new DarculaUIUtil.MouseHoverPropertyTrigger(myTextField, HOVER_PROPERTY);
    myTextField.addMouseListener(hoverListener);
  }

  @Override public void uninstallListeners() {
    super.uninstallListeners();
    if (hoverListener != null) {
      myTextField.removeMouseListener(hoverListener);
    }
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextComponent c = getComponent();
    if (UIUtil.getParentOfType(JComboBox.class, c) != null) return;

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
      if (isSearchField(c)) {
        paintSearchField(g2, c, getDrawingRect());
      }
    } finally {
      g2.dispose();
    }
  }

  static void paintTextFieldBackground(JComponent c, Graphics2D g2) {
    g2.setColor(c.isEnabled() ? c.getBackground() : UIManager.getColor("TextField.inactiveBackground"));

    if (!c.isEnabled()) {
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
    }

    Rectangle r = new Rectangle(c.getSize());
    if (UIUtil.getParentOfType(JSpinner.class, c) == null) { // Fill whole rectangle in spinner
      JBInsets.removeFrom(r, JBUI.insets(2));

      adjustInWrapperRect(r, c);
    }
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

  @Override public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    size.height = Math.max(JBUI.scale(24), size.height);
    return size;
  }

  @Override protected void paintSearchField(Graphics2D g, JTextComponent c, Rectangle r) {
    Icon searchIcon = isSearchFieldWithHistoryPopup(c) ?
                      UIManager.getIcon("TextField.darcula.searchWithHistory.icon") :
                      UIManager.getIcon("TextField.darcula.search.icon");
    if (searchIcon == null) {
      searchIcon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/search.png", DarculaTextFieldUI.class, true);
    }

    if (searchIcon != null) {
      int yOffset = isSearchFieldWithHistoryPopup(c) ? JBUI.scale(1) : 0;
      searchIcon.paintIcon(c, g, JBUI.scale(5), (c.getHeight() - searchIcon.getIconHeight()) / 2 + yOffset);
    }

    if (hasText()) {
      Icon clearIcon = UIManager.getIcon("TextField.darcula.clear.icon");
      if (clearIcon == null) {
        clearIcon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/clear.png", DarculaTextFieldUI.class, true);
      }

      if (clearIcon != null) {
        clearIcon.paintIcon(c, g, c.getWidth() - clearIcon.getIconWidth() - JBUI.scale(5),
                            (c.getHeight() - clearIcon.getIconHeight()) / 2);
      }
    }
  }
}
