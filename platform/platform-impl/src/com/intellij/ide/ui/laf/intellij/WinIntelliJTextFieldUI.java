// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isCompact;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isTableCellEditor;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextBorder.MINIMUM_HEIGHT;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextFieldUI extends TextFieldWithPopupHandlerUI {
  public static final String HOVER_PROPERTY = "JTextField.hover";

  private MouseListener hoverListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJTextFieldUI();
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
    if (ComponentUtil.getParentOfType((Class<? extends JComboBox>)JComboBox.class, (Component)c) != null) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Container parent = c.getParent();
      if (c.isOpaque() && parent != null) {
        g2.setColor(parent.getBackground());
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
      }

      if (c.getBorder() instanceof WinIntelliJTextBorder) {
        paintTextFieldBackground(c, g2);
      }
      else if (c.isOpaque()) {
        super.paintBackground(g);
      }
    }
    finally {
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
    if (ComponentUtil.getParentOfType((Class<? extends Wrapper>)Wrapper.class, c) != null && isSearchFieldWithHistoryPopup(c)) {
      int delta = c.getHeight() - c.getPreferredSize().height;
      if (delta > 0) {
        delta -= delta % 2 == 0 ? 0 : 1;
        JBInsets.removeFrom(r, JBInsets.create(delta / 2, 0));
      }
    }
  }

  @Override
  protected int getMinimumHeight(int textHeight) {
    JComponent c = getComponent();
    Insets i = c.getInsets();
    return DarculaEditorTextFieldBorder.isComboBoxEditor(c) || ComponentUtil.getParentOfType((Class<? extends JSpinner>)JSpinner.class,
                                                                                             (Component)c) != null ?
           textHeight : MINIMUM_HEIGHT.get() + i.top + i.bottom;
  }

  @Override
  protected int getSearchIconGap() {
    return 0;
  }

  @Override
  protected Insets getDefaultMargins() {
    Component c = getComponent();
    return isCompact(c) || isTableCellEditor(c) ? JBInsets.create(0, 3) : JBInsets.create(2, 6);
  }

  @Override
  protected int getClearIconGap() {
    return JBUIScale.scale(3);
  }
}
