/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.ComboBoxCompositeEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
  @SuppressWarnings("UseJBColor")
  private static final Color MAC_ACTIVE_ERROR_COLOR = new Color(0x80f53b3b, true);
  private static final Color DEFAULT_ACTIVE_ERROR_COLOR = new JBColor(0xe53e4d, 0x8b3c3c);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_INACTIVE_ERROR_COLOR = new Color(0x80f7bcbc, true);
  private static final Color DEFAULT_INACTIVE_ERROR_COLOR = new JBColor(0xebbcbc, 0x725252);

  public static final Color ACTIVE_ERROR_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_ACTIVE_ERROR_COLOR : DEFAULT_ACTIVE_ERROR_COLOR);
  public static final Color INACTIVE_ERROR_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_INACTIVE_ERROR_COLOR : DEFAULT_INACTIVE_ERROR_COLOR);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_ACTIVE_WARNING_COLOR = new Color(0x80e9ad43, true);
  private static final Color DEFAULT_ACTIVE_WARNING_COLOR = new JBColor(0xe2a53a, 0xac7920);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_INACTIVE_WARNING_COLOR = new Color(0x80ffda99, true);
  private static final Color DEFAULT_INACTIVE_WARNING_COLOR = new JBColor(0xffd385, 0x6e5324);

  public static final Color ACTIVE_WARNING_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_ACTIVE_WARNING_COLOR : DEFAULT_ACTIVE_WARNING_COLOR);
  public static final Color INACTIVE_WARNING_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_INACTIVE_WARNING_COLOR : DEFAULT_INACTIVE_WARNING_COLOR);

  private static final Color REGULAR_COLOR = new JBColor(new Color(0x80479cfc, true), new Color(0x395d82));
  private static final Color GRAPHITE_COLOR = new JBColor(new Color(0x8099979d, true), new Color(0x676869));

  public enum Outline {
    error {
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        g.setColor(focused ? ACTIVE_ERROR_COLOR : INACTIVE_ERROR_COLOR);
      }
    },

    warning {
      public void setGraphicsColor(Graphics2D g, boolean focused) {
        g.setColor(focused ? ACTIVE_WARNING_COLOR: INACTIVE_WARNING_COLOR);
      }
    };

    abstract public void setGraphicsColor(Graphics2D g, boolean focused);
  }

  public static void paintFocusOval(Graphics2D g, float x, float y, float width, float height) {
    g.setPaint(IntelliJLaf.isGraphite() ? GRAPHITE_COLOR : REGULAR_COLOR);

    float blw = bw() + lw(g);
    Path2D shape = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    shape.append(new Ellipse2D.Float(x - blw, y - blw, width + blw*2, height + blw*2), false);
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

  public static void paintFocusBorder(Graphics2D g, int width, int height, int arc, boolean symmetric) {
    paintFocusBorder(g, width, height, (float)arc, symmetric);
  }

  public static void paintFocusBorder(Graphics2D g, int width, int height, float arc, boolean symmetric) {
    g.setPaint(IntelliJLaf.isGraphite() ? GRAPHITE_COLOR : REGULAR_COLOR);
    doPaint(g, width, height, arc, symmetric);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private static void doPaint(Graphics2D g, int width, int height, float arc, boolean symmetric) {
    float bw = UIUtil.isUnderDefaultMacTheme() ? JBUI.scale(3) : bw();
    float lw = UIUtil.isUnderDefaultMacTheme() ? JBUI.scale(UIUtil.isRetina(g) ? 0.5f : 1.0f) : JBUI.scale(0.5f);

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
      return isComboBoxEditor(c) ?
             JBUI.insets(1, 3, 2, 3).asUIResource() :
             JBUI.insets(5, 8).asUIResource();
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

  public static float lw(Graphics2D g2) {
    return UIUtil.isJreHiDPI(g2) ? JBUI.scale(0.5f) : 1.0f;
  }

  public static float bw() {
    return JBUI.scale(2);
  }

  public static float arc() {
    return JBUI.scale(5.0f);
  }

  public static Color getOutlineColor(boolean enabled) {
    if (UIUtil.isUnderDarcula()) {
      return enabled ? Gray._100 : Gray._83;
    }
    return Gray.xBC ;
  }

  public static Color getArrowButtonFillColor(boolean hasFocus, boolean enabled, Color defaultColor) {
    Color color = UIManager.getColor(hasFocus ? "ComboBox.darcula.arrowFocusedFillColor" : "ComboBox.darcula.arrowFillColor");
    return color == null ? defaultColor : enabled ? color : getOutlineColor(false);
  }

  public static boolean isEmpty(Dimension d) {
    return d == null || d.width == 0 && d.height == 0;
  }
}
