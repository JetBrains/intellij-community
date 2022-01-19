// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.MINIMUM_HEIGHT;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnregisteredNamedColor")
public class DarculaButtonUI extends BasicButtonUI {
  private final Rectangle viewRect = new Rectangle();
  private final Rectangle textRect = new Rectangle();
  private final Rectangle iconRect = new Rectangle();

  protected static JBValue HELP_BUTTON_DIAMETER = new JBValue.Float(22);
  protected static JBValue MINIMUM_BUTTON_WIDTH = new JBValue.Float(72);
  protected static JBValue HORIZONTAL_PADDING = new JBValue.Float(14);

  private static final Color GOTIT_BUTTON_COLOR_START =
    JBColor.namedColor("GotItTooltip.startBackground", JBUI.CurrentTheme.Button.buttonColorStart());
  private static final Color GOTIT_BUTTON_COLOR_END =
    JBColor.namedColor("GotItTooltip.endBackground", JBUI.CurrentTheme.Button.buttonColorEnd());

  public static final Key<Boolean> DEFAULT_STYLE_KEY = Key.create("JButton.styleDefault");

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaButtonUI();
  }

  public static boolean isSquare(Component c) {
    return c instanceof AbstractButton && "square".equals(((AbstractButton)c).getClientProperty("JButton.buttonType"));
  }

  public static boolean isDefaultButton(JComponent c) {
    return c instanceof JButton &&
           (((JButton)c).isDefaultButton() || ComponentUtil.getClientProperty(c, DEFAULT_STYLE_KEY) == Boolean.TRUE);
  }

  public static boolean isSmallVariant(Component c) {
    if (!(c instanceof AbstractButton)) return false;

    AbstractButton b = (AbstractButton)c;
    boolean smallVariant = b.getClientProperty("ActionToolbar.smallVariant") == Boolean.TRUE;
    ComboBoxAction a = (ComboBoxAction)b.getClientProperty("styleCombo");

    return smallVariant || a != null && a.isSmallVariant();
  }

  public static boolean isTag(Component c) {
    return c instanceof AbstractButton && ((AbstractButton)c).getClientProperty("styleTag") != null;
  }

  public static boolean isComboAction(Component c) {
    return c instanceof AbstractButton && ((JComponent)c).getClientProperty("styleCombo") != null;
  }

  public static boolean isGotItButton(Component c) {
    return c instanceof AbstractButton && ((JComponent)c).getClientProperty("gotItButton") == Boolean.TRUE;
  }

  @Override
  public void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setIconTextGap(textIconGap());
    b.setMargin(JBInsets.emptyInsets());
  }

  protected int textIconGap() {
    return JBUIScale.scale(4);
  }

  /**
   * Paints additional buttons decorations
   *
   * @param g Graphics
   * @param c button component
   * @return {@code true} if it is allowed to continue painting,
   * {@code false} if painting should be stopped
   */
  protected boolean paintDecorations(Graphics2D g, JComponent c) {
    if (!((AbstractButton)c).isContentAreaFilled()) {
      return true;
    }
    Rectangle r = new Rectangle(c.getSize());

    if(SegmentedActionToolbarComponent.Companion.isCustomBar(c)) {
      return SegmentedActionToolbarComponent.Companion.paintButtonDecorations(g, c, getBackground(c, r));
    }

    JBInsets.removeFrom(r, isSmallVariant(c) || isGotItButton(c) ? c.getInsets() : JBUI.insets(1));

    if (UIUtil.isHelpButton(c)) {
      g.setPaint(UIUtil.getGradientPaint(0, 0, getButtonColorStart(), 0, r.height, getButtonColorEnd()));
      int diam = HELP_BUTTON_DIAMETER.get();
      int x = r.x + (r.width - diam) / 2;
      int y = r.x + (r.height - diam) / 2;

      g.fill(new Ellipse2D.Float(x, y, diam, diam));
      AllIcons.Actions.Help.paintIcon(c, g, x + JBUIScale.scale(3), y + JBUIScale.scale(3));
      return false;
    }
    else {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        g2.translate(r.x, r.y);

        float bw = isSmallVariant(c) || isGotItButton(c) ? 0 : BW.getFloat();
        float arc = isTag(c) ? r.height - bw * 2 : DarculaUIUtil.BUTTON_ARC.getFloat();

        if (!c.hasFocus() && !isSmallVariant(c) && c.isEnabled() && UIManager.getBoolean("Button.paintShadow")) {
          Color shadowColor = JBColor.namedColor("Button.shadowColor", JBColor.namedColor("Button.darcula.shadowColor",
                                                  new JBColor(new Color(0xa6a6a633, true), new Color(0x36363680, true))));

          int shadowWidth = JBUIScale.scale(JBUI.getInt("Button.shadowWidth", 2));
          g2.setColor(isDefaultButton(c) ? JBColor.namedColor("Button.default.shadowColor", shadowColor) : shadowColor);
          g2.fill(new RoundRectangle2D.Float(bw, bw + shadowWidth, r.width - bw * 2, r.height - bw * 2, arc, arc));
        }

        if (c.isEnabled()) {
          g2.setPaint(getBackground(c, r));
          g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
        }
      }
      finally {
        g2.dispose();
      }
      return true;
    }
  }

  private Paint getBackground(JComponent c, Rectangle r) {
    Color backgroundColor = (Color)c.getClientProperty("JButton.backgroundColor");

    return backgroundColor != null ? backgroundColor :
           isDefaultButton(c) ? UIUtil.getGradientPaint(0, 0, getDefaultButtonColorStart(), 0, r.height, getDefaultButtonColorEnd()) :
           isSmallVariant(c) ? JBColor.namedColor("ComboBoxButton.background",
                                                  JBColor.namedColor("Button.darcula.smallComboButtonBackground", UIUtil.getPanelBackground())) :
           isGotItButton(c) ? UIUtil.getGradientPaint(0, 0, GOTIT_BUTTON_COLOR_START, 0, r.height, GOTIT_BUTTON_COLOR_END) :
           UIUtil.getGradientPaint(0, 0, getButtonColorStart(), 0, r.height, getButtonColorEnd());
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    if (paintDecorations((Graphics2D)g, c)) {
      paintContents(g, (AbstractButton)c);
    }
  }

  @Override
  protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
    if (UIUtil.isHelpButton(c)) {
      return;
    }

    AbstractButton button = (AbstractButton)c;
    ButtonModel model = button.getModel();
    g.setColor(getButtonTextColor(button));

    FontMetrics metrics = UIUtilities.getFontMetrics(c, g);
    if (model.isEnabled()) {

      UIUtilities.drawStringUnderlineCharAt(
        c, g, text, getMnemonicIndex(button),
        textRect.x + getTextShiftOffset(),
        textRect.y + metrics.getAscent() + getTextShiftOffset());
    }
    else {
      paintDisabledText(g, text, c, textRect, metrics);
    }
  }

  protected int getMnemonicIndex(AbstractButton b) {
    return DarculaLaf.isAltPressed() ? b.getDisplayedMnemonicIndex() : -1;
  }

  protected Color getButtonTextColor(AbstractButton button) {
    Color textColor = (Color)button.getClientProperty("JButton.textColor");
    return textColor != null ? textColor : DarculaUIUtil.getButtonTextColor(button);
  }

  public static Color getDisabledTextColor() {
    return UIManager.getColor("Button.disabledText");
  }

  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    g.setColor(UIManager.getColor("Button.disabledText"));
    UIUtilities.drawStringUnderlineCharAt(
      c, g, text, -1,
      textRect.x + getTextShiftOffset(),
      textRect.y + metrics.getAscent() + getTextShiftOffset());
  }

  protected void paintContents(Graphics g, AbstractButton b) {
    if (b instanceof JBOptionButton) return;

    FontMetrics fm = UIUtilities.getFontMetrics(b, g);
    boolean isDotButton = isSquare(b) && b.getIcon() == AllIcons.General.Ellipsis;
    @NlsSafe String text = isDotButton ? "..." : b.getText();
    Icon icon = isDotButton ? null : b.getIcon();
    text = layout(b, text, icon, fm, b.getWidth(), b.getHeight());

    if (isSquare(b)) {
      if (b.getIcon() == AllIcons.General.Ellipsis) {
        UISettings.setupAntialiasing(g);
        paintText(g, b, textRect, text);
      }
      else if (b.getIcon() != null) {
        paintIcon(g, b, iconRect);
      }
    }
    else {
      // Paint the Icon
      if (b.getIcon() != null) {
        paintIcon(g, b, iconRect);
      }

      if (text != null && !text.isEmpty()) {
        View v = (View)b.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
          v.paint(g, textRect);
        }
        else {
          UISettings.setupAntialiasing(g);
          paintText(g, b, textRect, text);
        }
      }
    }
  }

  protected Dimension getDarculaButtonSize(JComponent c, Dimension prefSize) {
    Insets i = c.getInsets();
    prefSize = ObjectUtils.notNull(prefSize, JBUI.emptySize());

    if (UIUtil.isHelpButton(c) || isSquare(c)) {
      int helpDiam = HELP_BUTTON_DIAMETER.get();
      return new Dimension(Math.max(prefSize.width, helpDiam + i.left + i.right),
                           Math.max(prefSize.height, helpDiam + i.top + i.bottom));
    }
    else {
      int width = isComboAction(c) ? prefSize.width :
                  Math.max(HORIZONTAL_PADDING.get() * 2 + prefSize.width, MINIMUM_BUTTON_WIDTH.get() + i.left + i.right);
      int height = Math.max(prefSize.height,
                            (isSmallVariant(c) ? ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height : getMinimumHeight()) + i.top + i.bottom);

      return new Dimension(width, height);
    }
  }

  protected int getMinimumHeight() {
    return MINIMUM_HEIGHT.get();
  }

  @Override
  public final Dimension getPreferredSize(JComponent c) {
    AbstractButton b = (AbstractButton)c;
    int textIconGap = StringUtil.isEmpty(b.getText()) || b.getIcon() == null ? 0 : b.getIconTextGap();
    Dimension size = BasicGraphicsUtils.getPreferredButtonSize(b, textIconGap);
    return getDarculaButtonSize(c, size);
  }

  @Override
  public void update(Graphics g, JComponent c) {
    setupDefaultButton(c, g);
    super.update(g, c);
  }

  protected void setupDefaultButton(JComponent button, Graphics g) {
    Font f = button.getFont();
    if (!SystemInfo.isMac && f instanceof FontUIResource && isDefaultButton(button)) {
      g.setFont(f.deriveFont(Font.BOLD));
    }
  }

  protected Color getButtonColorStart() {
    return JBUI.CurrentTheme.Button.buttonColorStart();
  }

  protected Color getButtonColorEnd() {
    return JBUI.CurrentTheme.Button.buttonColorEnd();
  }

  protected Color getDefaultButtonColorStart() {
    return JBUI.CurrentTheme.Button.defaultButtonColorStart();
  }

  protected Color getDefaultButtonColorEnd() {
    return JBUI.CurrentTheme.Button.defaultButtonColorEnd();
  }

  protected String layout(AbstractButton b, @Nls String text, Icon icon, FontMetrics fm, int width, int height) {
    textRect.setBounds(0, 0, 0, 0);
    iconRect.setBounds(0, 0, 0, 0);

    viewRect.setBounds(0, 0, width, height);
    modifyViewRect(b, viewRect);

    // layout the text and icon
    return SwingUtilities.layoutCompoundLabel(
      b, fm, text, icon,
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      StringUtil.isEmpty(text) || icon == null ? 0 : b.getIconTextGap());
  }

  protected void modifyViewRect(AbstractButton b, Rectangle rect) {
    JBInsets.removeFrom(rect, b.getInsets());
    JBInsets.removeFrom(rect, b.getMargin());
  }
}