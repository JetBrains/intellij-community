// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.LookAndFeelThemeAdapter;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalCheckBoxUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isMultiLineHTML;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaCheckBoxUI extends MetalCheckBoxUI {

  private static Icon defaultIconCache;

  private final PropertyChangeListener textChangedListener = e -> updateTextPosition((AbstractButton)e.getSource());

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaCheckBoxUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    if (ComponentUtil.getParentOfType(CellRendererPane.class, c) != null) {
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
    return JBUIScale.scale(JBUI.getInt("CheckBox.textIconGap", 5));
  }

  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    AbstractButton button = (AbstractButton)c;
    AbstractButtonLayout layout = createLayout(button, button.getSize());

    layout.paint(g, getDisabledTextColor(), getMnemonicIndex(button));
    drawCheckIcon(c, g, button, layout.iconRect, button.isSelected(), button.isEnabled());
  }

  @ApiStatus.Internal
  public @NotNull Rectangle getTextRect(@NotNull JCheckBox b) {
    return createLayout(b, b.getSize()).textRect;
  }

  @Override
  public int getBaseline(JComponent c, int width, int height) {
    AbstractButtonLayout layout = createLayout(c, new Dimension(width, height));
    return layout.getBaseline();
  }

  protected boolean removeInsetsBeforeLayout(AbstractButton b) {
    return !(b.getBorder() instanceof DarculaCheckBoxBorder);
  }

  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      String iconName = isIndeterminate(b) ? "checkBoxIndeterminate" : "checkBox";

      DarculaUIUtil.Outline op = DarculaUIUtil.getOutline(b);
      boolean hasFocus = op == null && b.hasFocus();
      Icon icon = LafIconLookup.getIcon(iconName, selected || isIndeterminate(b), hasFocus, b.isEnabled());
      icon.paintIcon(b, g2, iconRect.x, iconRect.y);

      if (op != null) {
        op.setGraphicsColor(g2, b.hasFocus());
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(new RoundRectangle2D.Float(iconRect.x + JBUIScale.scale(3), iconRect.y + JBUIScale.scale(3),
                                                  JBUIScale.scale(18), JBUIScale.scale(18),
                                                  JBUIScale.scale(8), JBUIScale.scale(8)), false);
        outline.append(new RoundRectangle2D.Float(iconRect.x + JBUIScale.scale(5),  iconRect.y + JBUIScale.scale(5),
                                                  JBUIScale.scale(14), JBUIScale.scale(14),
                                                  JBUIScale.scale(4), JBUIScale.scale(4)), false);

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

  protected int getMnemonicIndex(AbstractButton b) {
    return LookAndFeelThemeAdapter.isAltPressed() ? b.getDisplayedMnemonicIndex() : -1;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    AbstractButtonLayout layout = createLayout(c, new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
    return layout.getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return getPreferredSize(c);
  }

  @Override
  public Icon getDefaultIcon() {
    int iconSize = JBUI.getInt("CheckBox.iconSize", 18);
    if (defaultIconCache == null || defaultIconCache.getIconWidth() != iconSize || defaultIconCache.getIconHeight() != iconSize) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      defaultIconCache = JBUIScale.scaleIcon(EmptyIcon.create(iconSize)).asUIResource();
    }
    return defaultIconCache;
  }

  protected boolean isIndeterminate(AbstractButton checkBox) {
    return "indeterminate".equals(checkBox.getClientProperty("JButton.selectedState")) ||
           checkBox instanceof ThreeStateCheckBox && ((ThreeStateCheckBox)checkBox).getState() == ThreeStateCheckBox.State.DONT_CARE;
  }

  private @NotNull AbstractButtonLayout createLayout(JComponent c, Dimension size) {
    AbstractButton button = (AbstractButton)c;
    return new AbstractButtonLayout(button, size, removeInsetsBeforeLayout(button), getDefaultIcon());
  }
}
