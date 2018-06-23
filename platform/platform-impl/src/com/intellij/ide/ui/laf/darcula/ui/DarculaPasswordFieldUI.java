// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.geom.Rectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.MINIMUM_HEIGHT;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaPasswordFieldUI extends BasicPasswordFieldUI {
  private FocusListener focusListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaPasswordFieldUI();
  }

  @Override
  public void installListeners() {
    super.installListeners();
    JTextComponent passwordField = getComponent();
    focusListener = new FocusListener() {
      @Override public void focusGained(FocusEvent e) {
        passwordField.repaint();
      }

      @Override public void focusLost(FocusEvent e) {
        passwordField.repaint();
      }
    };

    passwordField.addFocusListener(focusListener);
  }

  @Override
  public void uninstallListeners() {
    super.uninstallListeners();
    if (focusListener != null) {
      getComponent().removeFocusListener(focusListener);
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return updatePreferredSize(super.getPreferredSize(c));
  }

  protected Dimension updatePreferredSize(Dimension size) {
    Insets i = getComponent().getInsets();
    size.height = Math.max(size.height, getMinimumHeight() + i.top + i.bottom);
    JBInsets.addTo(size, getComponent().getMargin());
    return size;
  }

  protected int getMinimumHeight() {
    return MINIMUM_HEIGHT.get();
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getPreferredSize(c);
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

      Graphics2D g2 = (Graphics2D)g.create();
      Rectangle r = new Rectangle(component.getSize());
      JBInsets.removeFrom(r, JBUI.insets(1));

      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.translate(r.x, r.y);

        float bw = DarculaUIUtil.BW.getFloat();

        if (component.isEnabled() && component.isEditable()) {
          g2.setColor(component.getBackground());
        }

        g2.fill(new Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2));
      } finally {
        g2.dispose();
      }
    }
  }

  @Override
  protected Rectangle getVisibleEditorRect() {
    JTextComponent c = getComponent();
    Rectangle bounds = new Rectangle(c.getSize());
    JBInsets.removeFrom(bounds, c.getInsets());
    JBInsets.removeFrom(bounds, c.getMargin());
    return bounds;
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    getComponent().setMargin(JBUI.insets(0, 5));
  }

  @Override
  protected Caret createCaret() {
    return new TextFieldWithPopupHandlerUI.MarginAwareCaret();
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();

    JTextComponent component = getComponent();
    if (SystemInfoRt.isMac && SystemProperties.getBooleanProperty("idea.ui.set.password.echo.char", false)) {
      LookAndFeel.installProperty(component, "echoChar", '•');
    }
  }
}
