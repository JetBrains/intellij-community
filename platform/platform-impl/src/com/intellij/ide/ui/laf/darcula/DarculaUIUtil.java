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
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import static com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI.isSearchFieldWithHistoryPopup;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI.HOVER_PROPERTY;
import static com.intellij.util.ui.MacUIUtil.MAC_FILL_BORDER;
import static javax.swing.SwingConstants.EAST;
import static javax.swing.SwingConstants.WEST;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaUIUtil {
  private static final Color GLOW_COLOR = new JBColor(new Color(31, 121, 212), new Color(96, 175, 255));

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_ACTIVE_ERROR_COLOR = new Color(0x80f53b3b, true);
  private static final JBColor DEFAULT_ACTIVE_ERROR_COLOR = new JBColor(0xe53e4d, 0x8b3c3c);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_INACTIVE_ERROR_COLOR = new Color(0x80f7bcbc, true);
  private static final JBColor DEFAULT_INACTIVE_ERROR_COLOR = new JBColor(0xebbcbc, 0x725252);

  public static final Color ACTIVE_ERROR_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_ACTIVE_ERROR_COLOR : DEFAULT_ACTIVE_ERROR_COLOR);
  public static final Color INACTIVE_ERROR_COLOR = new JBColor(() -> UIUtil.isUnderDefaultMacTheme() ? MAC_INACTIVE_ERROR_COLOR : DEFAULT_INACTIVE_ERROR_COLOR);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_REGULAR_COLOR = new Color(0x80479cfc, true);

  @SuppressWarnings("UseJBColor")
  private static final Color MAC_GRAPHITE_COLOR = new Color(0x8099979d, true);

  public static void paintFocusRing(Graphics g, Rectangle bounds) {
    MacUIUtil.paintFocusRing((Graphics2D)g, GLOW_COLOR, bounds);
  }

  public static void paintFocusOval(Graphics g, int x, int y, int width, int height) {
    MacUIUtil.paintFocusRing((Graphics2D)g, GLOW_COLOR, new Rectangle(x, y, width, height), true);
  }

  public static void paintSearchFocusRing(Graphics2D g, Rectangle bounds, Component component) {
    paintSearchFocusRing(g, bounds, component, -1);
  }

  public static void paintSearchFocusRing(Graphics2D g, Rectangle bounds, Component component, int maxArcSize) {
    int correction = UIUtil.isUnderAquaLookAndFeel() ? 30 : UIUtil.isUnderDarcula() ? 50 : 0;
    final Color[] colors = new Color[]{
      ColorUtil.toAlpha(GLOW_COLOR, 180 - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 120 - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 70  - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 100 - correction),
      ColorUtil.toAlpha(GLOW_COLOR, 50  - correction)
    };

    final Object oldAntialiasingValue = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);


    final Rectangle r = new Rectangle(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);
    int arcSize = r.height - 1;
    if (maxArcSize>0) arcSize = Math.min(maxArcSize, arcSize);
    if (arcSize %2 == 1) arcSize--;


    g.setColor(component.getBackground());
    g.fillRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, arcSize - 4, arcSize - 4);

    g.setColor(colors[0]);
    g.drawRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, arcSize-4, arcSize-4);

    g.setColor(colors[1]);
    g.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, arcSize-2, arcSize-2);

    g.setColor(colors[2]);
    g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arcSize, arcSize);


    g.setColor(colors[3]);
    g.drawRoundRect(r.x+3, r.y+3, r.width - 7, r.height - 7, arcSize-6, arcSize-6);

    g.setColor(colors[4]);
    g.drawRoundRect(r.x+4, r.y+4, r.width - 9, r.height - 9, arcSize-8, arcSize-8);

    // restore rendering hints
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasingValue);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControlValue);
  }

  public static void paintErrorBorder(Graphics2D g, int width, int height, int arc, boolean symmetric, boolean hasFocus) {
    g.setPaint(hasFocus ? ACTIVE_ERROR_COLOR : INACTIVE_ERROR_COLOR);
    doPaint(g, width, height, arc, symmetric);
  }

  public static void paintFocusBorder(Graphics2D g, int width, int height, int arc, boolean symmetric) {
    g.setPaint(IntelliJLaf.isGraphite() ? MAC_GRAPHITE_COLOR : MAC_REGULAR_COLOR);
    doPaint(g, width, height, arc, symmetric);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private static void doPaint(Graphics2D g, int width, int height, int arc, boolean symmetric) {
    double bw = UIUtil.isUnderDefaultMacTheme() ? (UIUtil.isRetina(g) ? 0.5 : 1.0) : 0.0;
    double lw = JBUI.scale(UIUtil.isUnderDefaultMacTheme() ? 3 : 2);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

    double outerArc = arc > 0 ? arc + lw - JBUI.scale(2) : lw;
    double rightOuterArc = symmetric ? outerArc : JBUI.scale(6);
    Path2D outerRect = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    outerRect.moveTo(width - rightOuterArc, 0);
    outerRect.quadTo(width, 0, width, rightOuterArc);
    outerRect.lineTo(width, height - rightOuterArc);
    outerRect.quadTo(width, height, width - rightOuterArc, height);
    outerRect.lineTo(outerArc, height);
    outerRect.quadTo(0, height, 0, height - outerArc);
    outerRect.lineTo(0, outerArc);
    outerRect.quadTo(0, 0, outerArc, 0);
    outerRect.closePath();

    lw += bw;
    double rightInnerArc = symmetric ? outerArc : JBUI.scale(7);
    Path2D innerRect = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    innerRect.moveTo(width - rightInnerArc, lw);
    innerRect.quadTo(width - lw, lw , width - lw, rightInnerArc);
    innerRect.lineTo(width - lw, height - rightInnerArc);
    innerRect.quadTo(width - lw, height - lw, width - rightInnerArc, height - lw);
    innerRect.lineTo(outerArc, height - lw);
    innerRect.quadTo(lw, height - lw, lw, height - outerArc);
    innerRect.lineTo(lw, outerArc);
    innerRect.quadTo(lw, lw, outerArc, lw);
    innerRect.closePath();

    Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
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

      if (UIUtil.getParentOfType(EditorTextField.class, c) == null) {
        return;
      }

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        if (c.isOpaque() || (c instanceof JComponent && ((JComponent)c).getClientProperty(MAC_FILL_BORDER) == Boolean.TRUE)) {
          g2.setColor(UIUtil.getPanelBackground());
          g2.fillRect(x, y, width, height);
        }

        Rectangle2D rect = new Rectangle2D.Double(x + JBUI.scale(3), y + JBUI.scale(3), width - JBUI.scale(6), height - JBUI.scale(6));
        g2.setColor(c.getBackground());
        g2.fill(rect);

        if (!editorTextField.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }

        double bw = UIUtil.isRetina(g2) ? 0.5 : 1.0;
        Path2D outline = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        outline.append(rect, false);
        outline.append(new Rectangle2D.Double(rect.getX() + bw,
                                              rect.getY() + bw,
                                              rect.getWidth() - 2*bw,
                                              rect.getHeight() - 2*bw), false);
        g2.setColor(Gray.xBC);
        g2.fill(outline);

        if (editorTextField.isEnabled() && editorTextField.isVisible() && hasFocus(editorTextField)) {
          g2.translate(x, y);
          paintFocusBorder(g2, width, height, 0, true);
        }
      } finally {
        g2.dispose();
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return isComboBoxEditor(c) ? new InsetsUIResource(1, 3, 2, 3) : new InsetsUIResource(6, 7, 6, 7);
    }
  }

  public static class WinEditorTextFieldBorder extends DarculaEditorTextFieldBorder {
    private final JComponent editorComponent;

    public WinEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
      super(editorTextField, editor);
      editorComponent = editor.getComponent();

      editor.addEditorMouseListener(new EditorMouseAdapter() {
        @Override
        public void mouseEntered(EditorMouseEvent e) {
          JComponent c = e.getEditor().getComponent();
          c.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
          editorTextField.repaint();
        }

        @Override
        public void mouseExited(EditorMouseEvent e) {
          JComponent c = e.getEditor().getComponent();
          c.putClientProperty(HOVER_PROPERTY, Boolean.FALSE);
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

      if (UIUtil.getParentOfType(EditorTextField.class, c) == null) {
        return;
      }

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

          Path2D borderArea = new Path2D.Double(Path2D.WIND_EVEN_ODD);
          borderArea.append(r, false);

          Rectangle innerRect = new Rectangle(r);
          JBInsets.removeFrom(innerRect, JBUI.insets(2));
          borderArea.append(innerRect, false);
          g2.fill(borderArea);
        }

        // draw border itself
        if (hasFocus(editorTextField)) {
          g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
        } else if (editorTextField.isEnabled() &&
                   editorComponent != null && editorComponent.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE) {
          g2.setColor(UIManager.getColor("TextField.hoverBorderColor"));
        } else {
          g2.setColor(UIManager.getColor("TextField.borderColor"));
        }

        if (!editorTextField.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
        }


        Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        JBInsets.removeFrom(r, JBUI.insets(1));
        border.append(r, false);

        Rectangle innerRect = new Rectangle(r);
        JBInsets.removeFrom(innerRect, JBUI.insets(1));
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

  private static boolean hasFocus(@NotNull Component component) {
    if (component.hasFocus()) return true;
    if (!(component instanceof JComponent)) return false;

    List<Component> children;
    synchronized (component.getTreeLock()) {
      children = Arrays.asList(((Container)component).getComponents());
    }

    for (Component c : children) {
      if (hasFocus(c)) {
        return true;
      }
    }

    return false;
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
}
