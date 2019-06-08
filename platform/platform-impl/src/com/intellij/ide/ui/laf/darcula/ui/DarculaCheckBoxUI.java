// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalCheckBoxUI;
import javax.swing.text.View;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isMultiLineHTML;
import static com.intellij.util.ui.JBUI.scale;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaCheckBoxUI extends MetalCheckBoxUI {
  private static final Icon DEFAULT_ICON = scale(EmptyIcon.create(18)).asUIResource();

  private final PropertyChangeListener textChangedListener = e -> updateTextPosition((AbstractButton)e.getSource());

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaCheckBoxUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    if (ComponentUtil.getParentOfType((Class<? extends CellRendererPane>)CellRendererPane.class, (Component)c) != null) {
      c.setBorder(null);
    }
  }

  @Override
  public void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setIconTextGap(textIconGap());
    updateTextPosition(b);
  }

  private static void updateTextPosition(AbstractButton b) {
    b.setVerticalTextPosition(isMultiLineHTML(b.getText()) ? SwingConstants.TOP : SwingConstants.CENTER);
  }

  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    b.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, textChangedListener);
  }

  @Override
  protected void uninstallListeners(AbstractButton button) {
    super.uninstallListeners(button);
    button.removePropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, textChangedListener);
  }

  protected int textIconGap() {
    return JBUIScale.scale(5);
  }

  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    Dimension size = c.getSize();

    AbstractButton b = (AbstractButton)c;
    Rectangle viewRect = updateViewRect(b, new Rectangle(size));
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = UIUtilities.getFontMetrics(c, g, f);

    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), getDefaultIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, b.getIconTextGap());

    if (c.isOpaque()) {
      g.setColor(b.getBackground());
      g.fillRect(0, 0, size.width, size.height);
    }

    drawCheckIcon(c, g, b, iconRect, b.isSelected(), b.isEnabled());
    drawText(c, g, b, fm, textRect, text);
  }

  protected Rectangle updateViewRect(AbstractButton b, Rectangle viewRect) {
    if (!(b.getBorder() instanceof DarculaCheckBoxBorder)) {
      JBInsets.removeFrom(viewRect, b.getInsets());
    }
    return viewRect;
  }

  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      String iconName = isIndeterminate(b) ? "checkBoxIndeterminate" : "checkBox";

      Object op = b.getClientProperty("JComponent.outline");
      boolean hasFocus = op == null && b.hasFocus();
      Icon icon = LafIconLookup.getIcon(iconName, selected || isIndeterminate(b), hasFocus, b.isEnabled());
      icon.paintIcon(b, g2, iconRect.x, iconRect.y);

      if (op != null) {
        DarculaUIUtil.Outline.valueOf(op.toString()).setGraphicsColor(g2, b.hasFocus());
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(new RoundRectangle2D.Float(iconRect.x + JBUIScale.scale(1), iconRect.y, JBUIScale.scale(18), JBUIScale.scale(18),
                                                  JBUIScale.scale(8), JBUIScale.scale(8)), false);
        outline.append(new RoundRectangle2D.Float(iconRect.x + JBUIScale.scale(4), iconRect.y + JBUIScale.scale(3), JBUIScale.scale(12),
                                                  JBUIScale.scale(12), JBUIScale.scale(3), JBUIScale.scale(3)),
                       false);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.fill(outline);
      }
    }
    finally {
      g2.dispose();
    }
  }

  protected void drawText(JComponent c, Graphics2D g, AbstractButton b, FontMetrics fm, Rectangle textRect, String text) {
    if (text != null) {
      View v = (View)b.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      }
      else {
        g.setColor(b.isEnabled() ? b.getForeground() : getDisabledTextColor());
        int mnemonicIndex = DarculaLaf.isAltPressed() ? b.getDisplayedMnemonicIndex() : -1;
        UIUtilities.drawStringUnderlineCharAt(b, g, text, mnemonicIndex, textRect.x, textRect.y + fm.getAscent());
      }
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return updatePreferredSize(c, super.getPreferredSize(c));
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return getPreferredSize(c);
  }

  protected Dimension updatePreferredSize(JComponent c, Dimension size) {
    if (c.getBorder() instanceof DarculaCheckBoxBorder) {
      JBInsets.removeFrom(size, c.getInsets());
    }
    return size;
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }

  protected boolean isIndeterminate(AbstractButton checkBox) {
    return "indeterminate".equals(checkBox.getClientProperty("JButton.selectedState")) ||
           checkBox instanceof ThreeStateCheckBox && ((ThreeStateCheckBox)checkBox).getState() == ThreeStateCheckBox.State.DONT_CARE;
  }
}
