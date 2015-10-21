/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import sun.swing.DefaultLookup;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.DimensionUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("GtkPreferredJComboBoxRenderer")
public class DarculaComboBoxUI extends BasicComboBoxUI implements Border {
  private final JComboBox myComboBox;

  // Cached the size that the display needs to render the largest item
  private Dimension myDisplaySizeCache = JBUI.emptySize();
  private Insets myPadding;

  public DarculaComboBoxUI(JComboBox comboBox) {
    super();
    myComboBox = comboBox;
    myComboBox.setBorder(this);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaComboBoxUI(((JComboBox)c));
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    myPadding = UIManager.getInsets("ComboBox.padding");
  }


  protected JButton createArrowButton() {
    final Color bg = myComboBox.getBackground();
    final Color fg = myComboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {

      @Override
      public void paint(Graphics g2) {
        final Graphics2D g = (Graphics2D)g2;
        final GraphicsConfig config = new GraphicsConfig(g);

        final int w = getWidth();
        final int h = getHeight();
        if (!isTableCellEditor(myComboBox)) {
          g.setColor(getArrowButtonFillColor(UIUtil.getControlColor()));
          g.fillRect(0, 0, w, h);
        }
        g.setColor(comboBox.isEnabled() ? new JBColor(Gray._255, getForeground()) : new JBColor(Gray._255, getBorderColor()));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        final int tW = JBUI.scale(8);
        final int tH = JBUI.scale(6);
        final int xU = (w - tW) / 2;
        final int yU = (h - tH) / 2;
        g.translate(JBUI.scale(2), JBUI.scale(1));
        final Path2D.Double path = new Path2D.Double();
        path.moveTo(xU, yU);
        path.lineTo(xU + tW, yU);
        path.lineTo(xU + tW/2, yU + tH);
        path.lineTo(xU, yU);
        //path.moveTo(xU + 1, yU + 2);
        //path.lineTo(3 * xU + 1, yU + 2);
        //path.lineTo(2 * xU + 1, 3 * yU);
        //path.lineTo(xU + 1, yU + 2);
        path.closePath();
        g.fill(path);
        g.translate(-JBUI.scale(2), -JBUI.scale(1));
        if (!isTableCellEditor(myComboBox)) {
          g.setColor(getArrowButtonFillColor(getBorderColor()));
          g.drawLine(0, -1, 0, h);
        }
        config.restore();
      }

      @Override
      public Dimension getPreferredSize() {
        int size = getFont().getSize() + 4;
        if (size%2==1) size++;
        return new DimensionUIResource(size, size);
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  protected Color getArrowButtonFillColor(Color defaultColor) {
    final Color color = myComboBox.hasFocus() ? UIManager.getColor("ComboBox.darcula.arrowFocusedFillColor")
                        : UIManager.getColor("ComboBox.darcula.arrowFillColor");
    return color == null ? defaultColor : comboBox != null && !comboBox.isEnabled() ? new JBColor(getBorderColor(), UIUtil.getControlColor()) : color;
  }

  @Override
  protected Insets getInsets() {
    return JBUI.insets(4, 7, 4, 5).asUIResource();
  }

  protected Dimension getDisplaySize() {
    Dimension display = new Dimension();

    ListCellRenderer renderer = comboBox.getRenderer();
    if (renderer == null) {
      renderer = new DefaultListCellRenderer();
    }

    boolean sameBaseline = true;

    Object prototypeValue = comboBox.getPrototypeDisplayValue();
    if (prototypeValue != null) {
      display = getSizeForComponent(renderer.getListCellRendererComponent(listBox, prototypeValue, -1, false, false));
    } else {
      final ComboBoxModel model = comboBox.getModel();

      int baseline = -1;
      Dimension d;

      if (model.getSize() > 0) {
        for (int i = 0; i < model.getSize(); i++) {
          Object value = model.getElementAt(i);
          Component rendererComponent = renderer.getListCellRendererComponent(listBox, value, -1, false, false);
          d = getSizeForComponent(rendererComponent);
          if (sameBaseline && value != null && (!(value instanceof String) || !"".equals(value))) {
            int newBaseline = rendererComponent.getBaseline(d.width, d.height);
            if (newBaseline == -1) {
              sameBaseline = false;
            }
            else if (baseline == -1) {
              baseline = newBaseline;
            }
            else if (baseline != newBaseline) {
              sameBaseline = false;
            }
          }
          display.width = Math.max(display.width, d.width);
          display.height = Math.max(display.height, d.height);
        }
      }
      else {
        display = getDefaultSize();
        if (comboBox.isEditable()) {
          display.width = JBUI.scale(100);
        }
      }
    }

    JBInsets.addTo(display, myPadding);

    myDisplaySizeCache.setSize(display.width, display.height);

    return display;
  }

  protected Dimension getSizeForComponent(Component comp) {
    currentValuePane.add(comp);
    comp.setFont(comboBox.getFont());
    Dimension d = comp.getPreferredSize();
    currentValuePane.remove(comp);
    return d;
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    final Container parent = c.getParent();
    if (parent != null) {
      g.setColor(isTableCellEditor(c) && editor != null ? editor.getBackground() : parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }
    Rectangle r = rectangleForCurrentValue();
    if (!isTableCellEditor(c)) {
      paintBorder(c, g, 0, 0, c.getWidth(), c.getHeight());
      hasFocus = comboBox.hasFocus();
      paintCurrentValueBackground(g, r, hasFocus);
    }
    paintCurrentValue(g, r, hasFocus);
  }

  private static boolean isTableCellEditor(JComponent c) {
    return Boolean.TRUE.equals(c.getClientProperty("JComboBox.isTableCellEditor")) || c.getParent() instanceof JTable;
  }

  @Override
  protected Rectangle rectangleForCurrentValue() {
    final Rectangle r = super.rectangleForCurrentValue();
    r.x-= JBUI.scale(2);
    return r;
  }

  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    ListCellRenderer renderer = comboBox.getRenderer();
    Component c;

    c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
    if (!hasFocus || isPopupVisible(comboBox)) {
      c.setBackground(UIManager.getColor("ComboBox.background"));
    }
    c.setFont(comboBox.getFont());
    if (hasFocus && !isPopupVisible(comboBox)) {
      c.setForeground(listBox.getForeground());
      c.setBackground(listBox.getBackground());
    }
    else {
      if (comboBox.isEnabled()) {
        c.setForeground(comboBox.getForeground());
        c.setBackground(comboBox.getBackground());
      }
      else {
        c.setForeground(DefaultLookup.getColor(
          comboBox, this, "ComboBox.disabledForeground", null));
        c.setBackground(DefaultLookup.getColor(
          comboBox, this, "ComboBox.disabledBackground", null));
      }
    }
    // paint selection in table-cell-editor mode correctly
    boolean changeOpaque = c instanceof JComponent && isTableCellEditor(comboBox) && c.isOpaque();
    if (changeOpaque) {
      ((JComponent)c).setOpaque(false);
    }

    boolean shouldValidate = false;
    if (c instanceof JPanel) {
      shouldValidate = true;
    }

    Rectangle r = new Rectangle(bounds);
    JBInsets.removeFrom(r, myPadding);

    currentValuePane.paintComponent(g, c, comboBox, r.x, r.y, r.width, r.height, shouldValidate);
    // return opaque for combobox popup items painting
    if (changeOpaque) {
      ((JComponent)c).setOpaque(true);
    }
  }



  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
  }

  @Override
  protected ComboBoxEditor createEditor() {
    final ComboBoxEditor comboBoxEditor = super.createEditor();
    Component editor = comboBoxEditor == null ? null : comboBoxEditor.getEditorComponent();
    if (editor instanceof JComponent) {
      ((JComponent)editor).setBorder(JBUI.Borders.empty());
    }
    if (editor != null) {
      editor.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          process(e);
        }

        private void process(KeyEvent e) {
          final int code = e.getKeyCode();
          if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && e.getModifiers() == 0) {
            comboBox.dispatchEvent(e);
          }
        }

        @Override
        public void keyReleased(KeyEvent e) {
          process(e);
        }
      });
      editor.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          update();
        }

        void update() {
          if (comboBox != null) {
            comboBox.revalidate();
            comboBox.repaint();
          }
        }

        @Override
        public void focusLost(FocusEvent e) {
          update();
        }
      });
    }
    return comboBoxEditor;
  }

  @Override
  public void paintBorder(Component c, Graphics g2, int x, int y, int width, int height) {
    if (comboBox == null || arrowButton == null) {
      return; //NPE on LaF change
    }

    hasFocus = false;
    checkFocus();
    final Graphics2D g = (Graphics2D)g2.create();
    final Rectangle arrowButtonBounds = arrowButton.getBounds();
    final int xxx = arrowButtonBounds.x - JBUI.scale(5);
    final int H = height - JBUI.scale(2);
    final int W = width - JBUI.scale(2);

    final Shape clip = g.getClip();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    final int R = JBUI.scale(5);
    if (hasFocus) {
      g.clipRect(JBUI.scale(2), JBUI.scale(2), comboBox.getWidth()- JBUI.scale(4), comboBox.getHeight() - JBUI.scale(4));
    }
    final Color background = editor != null && comboBox.isEditable()
                             ? editor.getBackground()
                             : UIUtil.getPanelBackground();
    g.setColor(background);
    g.fillRoundRect(x + JBUI.scale(1), y + JBUI.scale(1), W, H, R, R);
    g.setColor(getArrowButtonFillColor(arrowButton.getBackground()));
    g.fillRoundRect(xxx, y + JBUI.scale(1), width - xxx, H, R, R);
    g.setColor(background);
    g.fillRect(xxx, y + JBUI.scale(1), JBUI.scale(5), H);

    final Color borderColor = getBorderColor();//ColorUtil.shift(UIUtil.getBorderColor(), 4);
    g.setColor(getArrowButtonFillColor(borderColor));
    int off = hasFocus ? 1 : 0;
    g.drawLine(xxx + JBUI.scale(5), y + JBUI.scale(1) + off, xxx + JBUI.scale(5), height - JBUI.scale(2));

    Rectangle r = rectangleForCurrentValue();
    paintCurrentValueBackground(g, r, hasFocus);
    paintCurrentValue(g, r, false);

    if (hasFocus) {
      g.setClip(clip);
      DarculaUIUtil.paintFocusRing(g, JBUI.scale(2), JBUI.scale(2), width - JBUI.scale(4), height - JBUI.scale(4));
    }
    else {
      g.setColor(borderColor);
      g.drawRoundRect(JBUI.scale(1), JBUI.scale(1), width - JBUI.scale(2), height - JBUI.scale(2), R, R);
      if (!UIUtil.isUnderDarcula() && comboBox.isEnabled()) {
        g.setColor(getArrowButtonFillColor(getBorderColor()));
        final int offX = xxx + JBUI.scale(5);
        g.clipRect(offX, y, width - offX, height);
        g.drawRoundRect(JBUI.scale(1), JBUI.scale(1), width - JBUI.scale(2), height - JBUI.scale(2), R, R);
      }
    }
    g.dispose();
  }

  private void checkFocus() {
    if (!comboBox.isEnabled()) {
      hasFocus = false;
      return;
    }

    hasFocus = hasFocus(comboBox);
    if (hasFocus) return;

    final ComboBoxEditor ed = comboBox.getEditor();
    editor = ed == null ? null : ed.getEditorComponent();
    if (editor != null) {
      hasFocus = hasFocus(editor);
    }
  }

  private static boolean hasFocus(Component c) {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return owner != null && SwingUtilities.isDescendingFrom(owner, c);
  }

  private Color getBorderColor() {
    if (comboBox != null && myComboBox.isEnabled()) {
      return new JBColor(Gray._150, Gray._100);
    }
    return new JBColor(Gray._150, Gray._88);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(4, 7, 4, 5);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}