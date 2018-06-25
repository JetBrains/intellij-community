// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.ComboBoxCompositeEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import static com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI.isSearchFieldWithHistoryPopup;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI.HOVER_PROPERTY;
import static com.intellij.util.ui.MacUIUtil.MAC_FILL_BORDER;
import static javax.swing.SwingConstants.EAST;
import static javax.swing.SwingConstants.WEST;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaUIUtil {
  public enum Outline {
    error {
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        g.setColor(JBUI.CurrentTheme.Focus.errorColor(focused));
      }
    },

    warning {
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        g.setColor(JBUI.CurrentTheme.Focus.warningColor(focused));
      }
    },

    defaultButton {
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        if (focused) {
          g.setColor(JBUI.CurrentTheme.Focus.defaultButtonColor());
        }
      }
    },

    focus {
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        if (focused) {
          g.setColor(JBUI.CurrentTheme.Focus.focusColor());
        }
      }
    };

    abstract public void setGraphicsColor(Graphics2D g, boolean focused);
  }

  /**
   * Deprecated in favor of {@link #paintFocusBorder(Graphics2D, int, int, float, boolean)}
   */
  @Deprecated
  public static void paintFocusRing(Graphics g, Rectangle r) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(r.x, r.y);
      paintFocusBorder(g2, r.width, r.height, COMPONENT_ARC.getFloat(), true);
    } finally {
      g2.dispose();
    }
  }

  public static void paintFocusOval(Graphics2D g, float x, float y, float width, float height) {
    Outline.focus.setGraphicsColor(g, true);

    float blw = BW.getFloat() + LW.getFloat();
    Path2D shape = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    shape.append(new Ellipse2D.Float(x - blw, y - blw, width + blw * 2, height + blw * 2), false);
    shape.append(new Ellipse2D.Float(x, y, width, height), false);
    g.fill(shape);
  }

  @Deprecated
  public static void paintErrorBorder(Graphics2D g, int width, int height, int arc, boolean symmetric, boolean hasFocus) {
    paintOutlineBorder(g, width, height, arc, symmetric, hasFocus, Outline.error);
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
    float bw = UIUtil.isUnderDefaultMacTheme() ? JBUI.scale(3) : BW.getFloat();
    float lw = UIUtil.isUnderDefaultMacTheme() ? JBUI.scale(UIUtil.isRetina(g) ? 0.5f : 1.0f) : LW.getFloat();

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

    float outerArc = arc > 0 ? arc + bw - JBUI.scale(2f) : bw;
    float rightOuterArc = symmetric ? outerArc : JBUI.scale(6f);
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
    float rightInnerArc = symmetric ? outerArc : JBUI.scale(7f);
    Path2D innerRect = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    innerRect.moveTo(width - rightInnerArc, bw);
    innerRect.quadTo(width - bw, bw , width - bw, rightInnerArc);
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

  public static boolean isCurrentEventShiftDownEvent() {
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    return (event instanceof KeyEvent && ((KeyEvent)event).isShiftDown());
  }

  /**
   * @see javax.swing.plaf.basic.BasicTextUI#getNextVisualPositionFrom(JTextComponent, int, Position.Bias, int, Position.Bias[])
   * @return -1 if visual position shouldn't be patched, otherwise selection start or selection end
   */
  public static int getPatchedNextVisualPositionFrom(JTextComponent t, int pos, int direction) {
    if (!isCurrentEventShiftDownEvent()) {
      if (direction == WEST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionEnd() == pos) {
        return t.getSelectionStart();
      }
      if (direction == EAST && t.getSelectionStart() < t.getSelectionEnd() && t.getSelectionStart() == pos) {
        return t.getSelectionEnd();
      }
    }
    return -1;
  }

  public static class MacEditorTextFieldBorder extends DarculaEditorTextFieldBorder {
    public MacEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
      super(editorTextField, editor);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      if (isComboBoxEditor(c)) {
        g.setColor(c.getBackground());
        g.fillRect(x, y, width, height);
        return;
      }

      EditorTextField editorTextField = UIUtil.getParentOfType(EditorTextField.class, c);
      if (editorTextField == null) return;

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        if (c.isOpaque() || (c instanceof JComponent && ((JComponent)c).getClientProperty(MAC_FILL_BORDER) == Boolean.TRUE)) {
          g2.setColor(UIUtil.getPanelBackground());
          g2.fillRect(x, y, width, height);
        }

        Rectangle2D rect = new Rectangle2D.Float(x + JBUI.scale(3), y + JBUI.scale(3), width - JBUI.scale(3)*2, height - JBUI.scale(3)*2);
        g2.setColor(c.getBackground());
        g2.fill(rect);

        if (!editorTextField.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }

        float bw = UIUtil.isRetina(g2) ? 0.5f : 1.0f;
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(rect, false);
        outline.append(new Rectangle2D.Float((float)rect.getX() + bw,
                                             (float)rect.getY() + bw,
                                             (float)rect.getWidth() - 2*bw,
                                             (float)rect.getHeight() - 2*bw), false);
        g2.setColor(Gray.xBC);
        g2.fill(outline);

        g2.translate(x, y);

        boolean hasFocus = editorTextField.getFocusTarget().hasFocus();
        Object op = editorTextField.getClientProperty("JComponent.outline");
        if (op != null) {
          paintOutlineBorder(g2, width, height, 0, true, hasFocus, Outline.valueOf(op.toString()));
        } else if (editorTextField.isEnabled() && editorTextField.isVisible() && hasFocus) {
          paintFocusBorder(g2, width, height, 0, true);
        }
      } finally {
        g2.dispose();
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return isComboBoxEditor(c) ? JBUI.insets(2, 3).asUIResource() : JBUI.insets(5, 8).asUIResource();
    }
  }

  public static class WinEditorTextFieldBorder extends DarculaEditorTextFieldBorder {
    public WinEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
      super(editorTextField, editor);
      editor.addEditorMouseListener(new EditorMouseAdapter() {
        @Override
        public void mouseEntered(EditorMouseEvent e) {
          editorTextField.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
          editorTextField.repaint();
        }

        @Override
        public void mouseExited(EditorMouseEvent e) {
          editorTextField.putClientProperty(HOVER_PROPERTY, Boolean.FALSE);
          editorTextField.repaint();
        }
      });
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      if (isComboBoxEditor(c)) {
        g.setColor(c.getBackground());
        g.fillRect(x, y, width, height);
        return;
      }

      EditorTextField editorTextField = UIUtil.getParentOfType(EditorTextField.class, c);
      if (editorTextField == null) return;

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        Rectangle r = new Rectangle(x, y, width, height);

        if (UIUtil.getParentOfType(Wrapper.class, c) != null && isSearchFieldWithHistoryPopup(c)) {
          JBInsets.removeFrom(r, JBUI.insets(2, 0));
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        // Fill background area of border
        if (isBorderOpaque() || c.getParent() != null) {
          g2.setColor(c.getParent().getBackground());

          Path2D borderArea = new Path2D.Float(Path2D.WIND_EVEN_ODD);
          borderArea.append(r, false);

          Rectangle innerRect = new Rectangle(r);
          JBInsets.removeFrom(innerRect, JBUI.insets(2));
          borderArea.append(innerRect, false);
          g2.fill(borderArea);
        }

        // draw border itself
        boolean hasFocus = editorTextField.getFocusTarget().hasFocus();
        int bw = 1;

        Object op = editorTextField.getClientProperty("JComponent.outline");
        if (op != null) {
          Outline.valueOf(op.toString()).setGraphicsColor(g2, c.hasFocus());
          bw = 2;
        } else {
          if (hasFocus) {
            g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
          } else if (editorTextField.isEnabled() &&
                     editorTextField.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE) {
            g2.setColor(UIManager.getColor("TextField.hoverBorderColor"));
          } else {
            g2.setColor(UIManager.getColor("TextField.borderColor"));
          }
          JBInsets.removeFrom(r, JBUI.insets(1));
        }

        if (!editorTextField.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
        }

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(r, false);

        Rectangle innerRect = new Rectangle(r);
        JBInsets.removeFrom(innerRect, JBUI.insets(bw));
        border.append(innerRect, false);

        g2.fill(border);
      } finally {
        g2.dispose();
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      if (UIUtil.getParentOfType(ComboBoxCompositeEditor.class, c) != null) {
        return JBUI.emptyInsets().asUIResource();
      } else {
        return isComboBoxEditor(c) ? JBUI.insets(1, 6).asUIResource() : JBUI.insets(4, 6).asUIResource();
      }
    }

    @Nullable
    @Override
    public Insets getVisualPaddings(@NotNull Component component) {
      return JBUI.insets(1);
    }
  }

  public static class MouseHoverPropertyTrigger extends MouseAdapter {
    private final JComponent repaintComponent;
    private final String     hoverProperty;

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

  public static final JBValue MINIMUM_WIDTH = new JBValue.Float(64);
  public static final JBValue MINIMUM_HEIGHT = new JBValue.Float(24);
  public static final JBValue COMPACT_HEIGHT = new JBValue.Float(20);
  public static final JBValue ARROW_BUTTON_WIDTH = new JBValue.Float(23);
  public static final JBValue LW = new JBValue.Float(1);
  public static final JBValue BW = new JBValue.UIInteger("Border.width", 2);
  public static final JBValue BUTTON_ARC = new JBValue.UIInteger("Button.arc", 6);
  public static final JBValue COMPONENT_ARC = new JBValue.UIInteger("Component.arc", 5);

  /**
   * @Deprecated use LW.get() instead
   */
  @SuppressWarnings("unused")
  @Deprecated
  public static float lw(Graphics2D g2) {
    return JBUI.scale(1.0f);
  }

  /**
   * @Deprecated use BW.get() instead
   */
  @Deprecated
  public static float bw() {
    return BW.getFloat();
  }

  /**
   * @Deprecated use COMPONENT_ARC.get() instead
   */
  @Deprecated
  public static float arc() {
    return COMPONENT_ARC.getFloat();
  }

  /**
   *
   * @Deprecated use BUTTON_ARC.get() instead
   */
  @Deprecated
  public static float buttonArc() {
    return BUTTON_ARC.get();
  }

  public static Insets paddings() {
    return JBUI.insets(1);
  }

  public static Color getOutlineColor(boolean enabled, boolean focused) {
    return enabled ?
            focused ? new JBColor(0x87AFDA, 0x466D94) : new JBColor(Gray.xBF, Gray._100) :
           new JBColor(Gray.xCF, Gray._100);
  }

  public static Color getArrowButtonBackgroundColor(boolean enabled, boolean editable) {
    return enabled ?
      editable ? JBColor.namedColor("ComboBox.darcula.editable.arrowButtonBackground", Gray.xFC) :
                 JBColor.namedColor("ComboBox.darcula.arrowButtonBackground", Gray.xFC)
      : UIUtil.getPanelBackground();
  }

  public static Color getArrowButtonForegroundColor(boolean enabled) {
    return enabled ?
      JBColor.namedColor("ComboBox.darcula.arrowButtonForeground", Gray.x66) :
      JBColor.namedColor("ComboBox.darcula.arrowButtonDisabledForeground", Gray.xAB);
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
      Color selectedFg = UIManager.getColor("Button.darcula.selectedButtonForeground");
      if (selectedFg != null) {
        return selectedFg;
      }
    }
    return fg;
  }
}
