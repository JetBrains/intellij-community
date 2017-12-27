/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import sun.swing.DefaultLookup;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("GtkPreferredJComboBoxRenderer")
public class DarculaComboBoxUI extends BasicComboBoxUI implements Border, ErrorBorderCapable {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaComboBoxUI();
  }

  protected KeyListener   editorKeyListener;
  protected FocusListener editorFocusListener;

  @Override
  protected void installDefaults() {
    super.installDefaults();
    comboBox.setBorder(this);
  }

  protected JButton createArrowButton() {
    Color bg = comboBox.getBackground();
    Color fg = comboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {

      @Override
      public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

          int w = getWidth();
          int h = getHeight();
          float bw = bw();
          float lw = lw(g2);
          float arc = arc() - bw - lw;

          Path2D innerShape = new Path2D.Float();
          innerShape.moveTo(lw, bw + lw);
          innerShape.lineTo(w - bw - lw - arc, bw + lw);
          innerShape.quadTo(w - bw - lw, bw + lw , w - bw - lw, bw + lw + arc);
          innerShape.lineTo(w - bw - lw, h - bw - lw - arc);
          innerShape.quadTo(w - bw - lw, h - bw - lw, w - bw - lw - arc, h - bw - lw);
          innerShape.lineTo(lw, h - bw - lw);
          innerShape.closePath();

          g2.setColor(getArrowButtonFillColor(comboBox.getBackground()));
          g2.fill(innerShape);

          // Paint vertical line
          g2.setColor(getArrowButtonFillColor(getOutlineColor(comboBox.isEnabled())));
          g2.fill(new Rectangle2D.Float(0, bw + lw, lw(g2), getHeight() - (bw + lw) * 2));

          g2.setColor(new JBColor(Gray._255, comboBox.isEnabled() ? getForeground() : getOutlineColor(comboBox.isEnabled())));
          g2.fill(getArrowShape());
        } finally {
          g2.dispose();
        }
      }

      private Shape getArrowShape() {
        int tW = JBUI.scale(8);
        int tH = JBUI.scale(6);
        int xU = (getWidth() - tW) / 2 - JBUI.scale(1);
        int yU = (getHeight() - tH) / 2 + JBUI.scale(1);

        Path2D path = new Path2D.Float();
        path.moveTo(xU, yU);
        path.lineTo(xU + tW, yU);
        path.lineTo(xU + tW/2, yU + tH);
        path.lineTo(xU, yU);
        path.closePath();
        return path;
      }

      @Override
      public Dimension getPreferredSize() {
        Insets i = comboBox.getInsets();
        return new Dimension(JBUI.scale(14) + i.left, JBUI.scale(18) + i.top + i.bottom);
      }
    };
    button.setBorder(JBUI.Borders.empty());
    button.setOpaque(false);
    return button;
  }

  protected Color getArrowButtonFillColor(Color defaultColor) {
    return DarculaUIUtil.getArrowButtonFillColor(comboBox.hasFocus(), comboBox.isEnabled(), defaultColor);
  }

  @Override
  protected Insets getInsets() {
    return JBUI.insets(3, 8, 3, 3).asUIResource();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Container parent = c.getParent();
    if (parent != null) {
      g.setColor(isTableCellEditor(c) && editor != null ? editor.getBackground() : parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      float bw = bw();
      float arc = arc();

      boolean editable = editor != null && comboBox.isEditable();
      Color background = editable && comboBox.isEnabled() ? editor.getBackground() : UIUtil.getPanelBackground();
      g2.setColor(background);

      Shape innerShape = new RoundRectangle2D.Float(bw, bw, c.getWidth() - bw * 2, c.getHeight() - bw * 2, arc, arc);
      g2.fill(innerShape);
    } finally {
      g2.dispose();
    }

    if (!comboBox.isEditable()) {
      checkFocus();
      Rectangle r = rectangleForCurrentValue();
      paintCurrentValue(g, r, hasFocus);
    }
  }

  protected static boolean isTableCellEditor(JComponent c) {
    return Boolean.TRUE.equals(c.getClientProperty("JComboBox.isTableCellEditor")) || c.getParent() instanceof JTable;
  }

  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    ListCellRenderer renderer = comboBox.getRenderer();
    Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);

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

    Insets iPad = null;
    if (c instanceof SimpleColoredComponent) {
      SimpleColoredComponent scc = (SimpleColoredComponent)c;
      iPad = scc.getIpad();
      scc.setIpad(JBUI.emptyInsets());
    }

    currentValuePane.paintComponent(g, c, comboBox, r.x, r.y, r.width, r.height, shouldValidate);

    // return opaque for combobox popup items painting
    if (changeOpaque) {
      ((JComponent)c).setOpaque(true);
    }

    if (c instanceof SimpleColoredComponent) {
      SimpleColoredComponent scc = (SimpleColoredComponent)c;
      scc.setIpad(iPad);
    }
  }

  @Override
  protected ComboBoxEditor createEditor() {
    ComboBoxEditor comboBoxEditor = super.createEditor();
    installEditorKeyListener(comboBoxEditor);
    return comboBoxEditor;
  }

  protected void installEditorKeyListener(@NotNull ComboBoxEditor cbe) {
    Component ec = cbe.getEditorComponent();
    if (ec != null) {
      editorKeyListener = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          process(e);
        }

        @Override
        public void keyReleased(KeyEvent e) {
          process(e);
        }

        private void process(KeyEvent e) {
          final int code = e.getKeyCode();
          if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && e.getModifiers() == 0) {
            comboBox.dispatchEvent(e);
          }
        }
      };

      ec.addKeyListener(editorKeyListener);
    }
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (comboBox == null || arrowButton == null) {
      return; //NPE on LaF change
    }

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      checkFocus();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      g2.translate(x, y);

      float lw = lw(g2);
      float bw = bw();
      float arc = arc();

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Float(bw, bw, width - bw * 2, height - bw * 2, arc, arc), false);
      border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, width - (bw + lw) * 2, height - (bw + lw) * 2, arc - lw, arc - lw), false);

      g2.setColor(getOutlineColor(c.isEnabled()));
      g2.fill(border);

      Object op = comboBox.getClientProperty("JComponent.outline");
      if (op != null) {
        paintOutlineBorder(g2, width, height, arc, true, hasFocus, Outline.valueOf(op.toString()));
      } else if (hasFocus){
        paintFocusBorder(g2, width, height, arc, true);
      }
    } finally {
      g2.dispose();
    }
  }

  protected void checkFocus() {
    hasFocus = false;
    if (!comboBox.isEnabled()) {
      hasFocus = false;
      return;
    }

    hasFocus = hasFocus(comboBox);
    if (hasFocus) return;

    ComboBoxEditor ed = comboBox.getEditor();
    if (ed != null) {
      hasFocus = hasFocus(ed.getEditorComponent());
    }
  }

  protected static boolean hasFocus(Component c) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return owner != null && SwingUtilities.isDescendingFrom(owner, c);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return getInsets();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  protected Dimension getSizeWithButton(Dimension d) {
    Insets i = getInsets();
    Dimension abSize = arrowButton.getPreferredSize();

    int editorHeight = editor != null ? editor.getPreferredSize().height + i.top + i.bottom : 0;
    int height = Math.max(Math.max(editorHeight, Math.max(abSize.height, d.height)), JBUI.scale(18) + i.top + i.bottom);
    int width = Math.max(d.width, abSize.width + JBUI.scale(10));

    return new Dimension(width, height);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getSizeWithButton(super.getPreferredSize(c));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getSizeWithButton(super.getMinimumSize(c));
  }

  @Override
  protected void configureEditor() {
    super.configureEditor();

    if (editor instanceof JComponent) {
      JComponent jEditor = (JComponent)editor;
      jEditor.setOpaque(false);
      jEditor.setBorder(JBUI.Borders.empty());

      editorFocusListener = new FocusAdapter() {
        @Override public void focusGained(FocusEvent e) {
          update();
        }
        @Override public void focusLost(FocusEvent e) {
          update();
        }

        private void update() {
          if (comboBox != null) {
            comboBox.repaint();
          }
        }
      };

      if (editor instanceof JTextComponent) {
        editor.addFocusListener(editorFocusListener);
      } else {
        EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
        if (etf != null) {
          etf.addFocusListener(editorFocusListener);
        }
      }
    }

    if (Registry.is("ide.ui.composite.editor.for.combobox")) {
      // BasicComboboxUI sets focusability depending on the combobox focusability.
      // JPanel usually is unfocusable and uneditable.
      // It could be set as an editor when people want to have a composite component as an editor.
      // In such cases we should restore unfocusable state for panels.
      if (editor instanceof JPanel) {
        editor.setFocusable(false);
      }
    }
  }

  @Override protected void unconfigureEditor() {
    super.unconfigureEditor();

    if (editorKeyListener != null) {
      editor.removeKeyListener(editorKeyListener);
    }

    if (editor instanceof JTextComponent) {
      if (editorFocusListener != null) {
        editor.removeFocusListener(editorFocusListener);
      }
    } else {
      EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
      if (etf != null) {
        if (editorFocusListener != null) {
          etf.removeFocusListener(editorFocusListener);
        }
      }
    }
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return new ComboBoxLayoutManager() {
      @Override
      public void layoutContainer(Container parent) {
        JComboBox cb = (JComboBox)parent;

        if (arrowButton != null) {
          Dimension aps = arrowButton.getPreferredSize();
          if (cb.getComponentOrientation().isLeftToRight()) {
            arrowButton.setBounds(cb.getWidth() - aps.width, 0, aps.width, cb.getHeight());
          } else {
            arrowButton.setBounds(0, 0, aps.width, cb.getHeight());
          }
        }
        layoutEditor();
      }
    };
  }

  protected void layoutEditor() {
    if (comboBox.isEditable() && editor != null) {
      Rectangle er = rectangleForCurrentValue();
      Dimension eps = editor.getPreferredSize();
      if (eps.height < er.height) {
        int delta = (er.height - eps.height) / 2;
        er.y += delta;
        er.height = eps.height;
      }
      editor.setBounds(er);
    }
  }
}