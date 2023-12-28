// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.JBTableRowEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnregisteredNamedColor")
public final class DarculaUIUtil {

  private static final @NotNull JBColor DISABLED_BORDER_BORDER =
    JBColor.namedColor("Component.disabledBorderColor", JBColor.namedColor("Outline.disabledColor", Gray.xCF));
  private static final @NotNull JBColor BORDER_COLOR =
    JBColor.namedColor("Component.borderColor", JBColor.namedColor("Outline.color", Gray.xBF));
  private static final @NotNull JBColor FOCUSED_BORDER_COLOR =
    JBColor.namedColor("Component.focusedBorderColor", JBColor.namedColor("Outline.focusedColor", 0x87AFDA));

  public enum Outline {
    error {
      @Override
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        g.setColor(JBUI.CurrentTheme.Focus.errorColor(focused));
      }
    },

    warning {
      @Override
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        g.setColor(JBUI.CurrentTheme.Focus.warningColor(focused));
      }
    },

    defaultButton {
      @Override
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        if (focused) {
          g.setColor(JBUI.CurrentTheme.Focus.defaultButtonColor());
        }
      }
    },

    focus {
      @Override
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        if (focused) {
          g.setColor(JBUI.CurrentTheme.Focus.focusColor());
        }
      }
    };

    public abstract void setGraphicsColor(Graphics2D g, boolean focused);
  }

  public static void paintFocusOval(Graphics2D g, float x, float y, float width, float height) {
    Outline.focus.setGraphicsColor(g, true);

    float blw = BW.getFloat() + LW.getFloat();
    Path2D shape = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    shape.append(new Ellipse2D.Float(x - blw, y - blw, width + blw * 2, height + blw * 2), false);
    shape.append(new Ellipse2D.Float(x, y, width, height), false);
    g.fill(shape);
  }

  public static Outline computeOutlineFor(Component c) {
    JComponent component = ObjectUtils.tryCast(c, JComponent.class);

    if (component != null) {
      Outline outline = getOutline(component);

      if (outline != null) {
        return outline;
      }
    }

    return Outline.focus;
  }

  public static @Nullable Outline getOutline(@NotNull JComponent component) {
    String outline = ObjectUtils.tryCast(component.getClientProperty("JComponent.outline"), String.class);
    return outline == null ? null : Outline.valueOf(outline);
  }

  public static boolean isWarningOrError(@Nullable Outline outline) {
    return outline == DarculaUIUtil.Outline.error || outline == DarculaUIUtil.Outline.warning;
  }

  public static void paintTag(Graphics2D g2, float width, float height, boolean hasFocus, Outline type) {
    if (type == Outline.focus && !hasFocus) {
      return;
    }
    else {
      type.setGraphicsColor(g2, hasFocus);
    }

    float bw = BW.getFloat();
    Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    border.append(new RoundRectangle2D.Float(0, 0, width, height, height, height), false);
    float inner = height - bw * 2;
    border.append(new RoundRectangle2D.Float(bw, bw, width - bw * 2, inner, inner, inner), false);
    g2.fill(border);
  }

  public static void paintOutlineBorder(Graphics2D g, int width, int height, float arc, boolean symmetric, boolean hasFocus, Outline type) {
    type.setGraphicsColor(g, hasFocus);
    doPaint(g, width, height, arc, symmetric);
  }

  public static void paintFocusBorder(Graphics2D g, int width, int height, float arc, boolean symmetric) {
    Outline.focus.setGraphicsColor(g, true);
    doPaint(g, width, height, arc, symmetric);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  public static void doPaint(Graphics2D g, int width, int height, float arc, boolean symmetric) {
    float bw = UIUtil.isUnderDefaultMacTheme() ? JBUIScale.scale(3) : BW.getFloat();
    doPaint(g, width, height, arc, bw, symmetric);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  public static void doPaint(Graphics2D g, int width, int height, float arc, float bw, boolean symmetric) {
    float f = UIUtil.isRetina(g) ? 0.5f : 1.0f;
    float lw = UIUtil.isUnderDefaultMacTheme() ? JBUIScale.scale(f) : LW.getFloat();

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                       MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

    float outerArc = arc > 0 ? arc + bw - JBUIScale.scale(2f) : bw;
    float rightOuterArc = symmetric ? outerArc : JBUIScale.scale(6f);
    Path2D outerRect = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    outerRect.moveTo(width - rightOuterArc, 0);
    outerRect.quadTo(width, 0, width, rightOuterArc);
    outerRect.lineTo(width, height - rightOuterArc);
    outerRect.quadTo(width, height, width - rightOuterArc, height);
    outerRect.lineTo(outerArc, height);
    outerRect.quadTo(0, height, 0, height - outerArc);
    outerRect.lineTo(0, outerArc);
    outerRect.quadTo(0, 0, outerArc, 0);
    outerRect.closePath();

    bw += lw;
    float rightInnerArc = symmetric ? outerArc : JBUIScale.scale(7f);
    Path2D innerRect = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    innerRect.moveTo(width - rightInnerArc, bw);
    innerRect.quadTo(width - bw, bw, width - bw, rightInnerArc);
    innerRect.lineTo(width - bw, height - rightInnerArc);
    innerRect.quadTo(width - bw, height - bw, width - rightInnerArc, height - bw);
    innerRect.lineTo(outerArc, height - bw);
    innerRect.quadTo(bw, height - bw, bw, height - outerArc);
    innerRect.lineTo(bw, outerArc);
    innerRect.quadTo(bw, bw, outerArc, bw);
    innerRect.closePath();

    Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    path.append(outerRect, false);
    path.append(innerRect, false);
    g.fill(path);
  }

  private static boolean isCurrentEventShiftDownEvent() {
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    return event instanceof KeyEvent && ((KeyEvent)event).isShiftDown();
  }

  /**
   * @return -1 if visual position shouldn't be patched, otherwise selection start or selection end
   * @see javax.swing.plaf.basic.BasicTextUI#getNextVisualPositionFrom(JTextComponent, int, Position.Bias, int, Position.Bias[])
   */
  public static int getPatchedNextVisualPositionFrom(JTextComponent t, int pos, int direction) {
    if (!isCurrentEventShiftDownEvent()) {
      if (direction == SwingConstants.WEST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionEnd() == pos) {
        return t.getSelectionStart();
      }
      if (direction == SwingConstants.EAST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionStart() == pos) {
        return t.getSelectionEnd();
      }
    }
    return -1;
  }

  public static void paintCellEditorBorder(Graphics2D g2, Component c, Rectangle r, boolean hasFocus) {
    g2 = (Graphics2D)g2.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      float bw = CELL_EDITOR_BW.getFloat();

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new Rectangle2D.Float(0, 0, r.width, r.height), false);
      border.append(new Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2), false);

      Outline op = getOutline((JComponent)c);
      if (op != null || hasFocus) {
        Outline outline = op == null ? Outline.focus : op;
        outline.setGraphicsColor(g2, true);
        g2.fill(border);
      }
    }
    finally {
      g2.dispose();
    }
  }

  public static final class MouseHoverPropertyTrigger extends MouseAdapter {
    private final JComponent repaintComponent;
    private final String hoverProperty;

    public MouseHoverPropertyTrigger(@NotNull JComponent repaintComponent, @NotNull String hoverProperty) {
      this.repaintComponent = repaintComponent;
      this.hoverProperty = hoverProperty;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      setHover((JComponent)e.getComponent(), Boolean.TRUE);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setHover((JComponent)e.getComponent(), Boolean.FALSE);
    }

    private void setHover(JComponent c, Boolean value) {
      if (c.isEnabled()) {
        c.putClientProperty(hoverProperty, value);
        repaintComponent.repaint();
      }
    }
  }

  public static final String COMPACT_PROPERTY = "JComponent.compactHeight";

  public static boolean isCompact(Component c) {
    return c instanceof JComponent && ((JComponent)c).getClientProperty(COMPACT_PROPERTY) == Boolean.TRUE;
  }

  public static boolean isTableCellEditor(Component c) {
    return Boolean.TRUE.equals(((JComponent)c).getClientProperty("JComboBox.isTableCellEditor")) ||
           ComponentUtil.findParentByCondition(c, p -> p instanceof JBTableRowEditor) == null &&
           ComponentUtil.findParentByCondition(c, p -> p instanceof JTable) != null;
  }

  public static boolean isBorderless(Component c) {
    return ((JComponent)c).getClientProperty("JComboBox.isBorderless") == Boolean.TRUE;
  }

  /**
   * @deprecated Use correspondent {@link JBUI.CurrentTheme.ComboBox#minimumSize()}, {@link JBUI.CurrentTheme.TextField#minimumSize()} etc
   */
  @Deprecated
  public static final JBValue MINIMUM_HEIGHT = new JBValue.Float(24);
  public static final JBValue COMPACT_HEIGHT = new JBValue.Float(20);
  public static final JBValue ARROW_BUTTON_WIDTH = new JBValue.Float(23);
  public static final JBValue LW = new JBValue.Float(1);
  public static final JBValue BW = new JBValue.UIInteger("Component.focusWidth", 2);
  private static final JBValue CELL_EDITOR_BW = new JBValue.UIInteger("CellEditor.border.width", 2);
  public static final JBValue BUTTON_ARC = new JBValue.UIInteger("Button.arc", 6);
  public static final JBValue COMPONENT_ARC = new JBValue.UIInteger("Component.arc", 5);

  public static Insets paddings() {
    return JBUI.insets(1);
  }

  public static Color getOutlineColor(boolean enabled, boolean focused) {
    if (enabled) return focused ? FOCUSED_BORDER_COLOR : BORDER_COLOR;
    return DISABLED_BORDER_BORDER;
  }

  public static Dimension maximize(@Nullable Dimension s1, @NotNull Dimension s2) {
    return isEmpty(s1) ? s2 : new Dimension(Math.max(s1.width, s2.width), Math.max(s1.height, s2.height));
  }

  private static boolean isEmpty(Dimension d) {
    return d == null || d.width == 0 && d.height == 0;
  }

  public static Color getButtonTextColor(@NotNull AbstractButton button) {
    Color fg = button.getForeground();
    if (fg instanceof UIResource && DarculaButtonUI.isDefaultButton(button)) {
      Color selectedFg = UIManager.getColor("Button.default.foreground");
      if (selectedFg == null) {
        selectedFg = UIManager.getColor("Button.darcula.selectedButtonForeground");
      }

      if (selectedFg != null) {
        return selectedFg;
      }
    }
    return fg;
  }

  public static boolean isMultiLineHTML(@Nullable String text) {
    if (text != null) {
      text = text.toLowerCase(Locale.getDefault());
      return BasicHTML.isHTMLString(text) &&
             (text.contains("<br>") || text.contains("<br/>"));
    }
    return false;
  }
}
