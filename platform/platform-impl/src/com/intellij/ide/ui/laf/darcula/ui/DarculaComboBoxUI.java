// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("GtkPreferredJComboBoxRenderer")
public class DarculaComboBoxUI extends BasicComboBoxUI implements Border, ErrorBorderCapable {

  private static final Color NON_EDITABLE_BACKGROUND = JBColor.namedColor("ComboBox.darcula.nonEditableBackground", new JBColor(0xfcfcfc, 0x3c3f41));

  public DarculaComboBoxUI() {}

  @SuppressWarnings("unused")
  @Deprecated
  public DarculaComboBoxUI(JComboBox c) {}

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaComboBoxUI();
  }

  private KeyListener            editorKeyListener;
  private FocusListener          editorFocusListener;
  private PropertyChangeListener propertyListener;

  @Override
  protected void installDefaults() {
    super.installDefaults();
    installDarculaDefaults();
  }

  @Override
  protected void uninstallDefaults() {
    super.uninstallDefaults();
    uninstallDarculaDefaults();
  }

  protected void installDarculaDefaults() {
    comboBox.setBorder(this);
  }

  protected void uninstallDarculaDefaults() {
    comboBox.setBorder(null);
  }

  @Override protected void installListeners() {
    super.installListeners();

    propertyListener = createPropertyListener();
    comboBox.addPropertyChangeListener(propertyListener);
  }

  @Override public void uninstallListeners() {
    super.uninstallListeners();

    if (propertyListener != null) {
      comboBox.removePropertyChangeListener(propertyListener);
      propertyListener = null;
    }
  }

  @Override
  protected ComboPopup createPopup() {
    return new CustomComboPopup(comboBox);
  }

  protected PropertyChangeListener createPropertyListener() {
    return e -> {
      if ("enabled".equals(e.getPropertyName())) {
        EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
        if (etf != null) {
          boolean enabled = e.getNewValue() == Boolean.TRUE;
          Color color = UIManager.getColor(enabled ? "TextField.background" : "ComboBox.disabledBackground");
          etf.setBackground(color);
        }
      }
    };
  }

  protected JButton createArrowButton() {
    Color bg = comboBox.getBackground();
    Color fg = comboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {

      @Override
      public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        Rectangle r = new Rectangle(getSize());
        JBInsets.removeFrom(r, JBUI.insets(1, 0, 1, 1));

        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
          g2.translate(r.x, r.y);

          float bw = BW.getFloat();
          float lw = LW.getFloat();
          float arc = COMPONENT_ARC.getFloat() - bw - lw;

          Path2D innerShape = new Path2D.Float();
          innerShape.moveTo(lw, bw + lw);
          innerShape.lineTo(r.width - bw - lw - arc, bw + lw);
          innerShape.quadTo(r.width - bw - lw, bw + lw , r.width - bw - lw, bw + lw + arc);
          innerShape.lineTo(r.width - bw - lw, r.height - bw - lw - arc);
          innerShape.quadTo(r.width - bw - lw, r.height - bw - lw, r.width - bw - lw - arc, r.height - bw - lw);
          innerShape.lineTo(lw, r.height - bw - lw);
          innerShape.closePath();

          g2.setColor(getArrowButtonBackgroundColor(comboBox.isEnabled(), comboBox.isEditable()));
          g2.fill(innerShape);

          // Paint vertical line
          if (comboBox.isEditable()) {
            g2.setColor(getOutlineColor(comboBox.isEnabled(), false));
            g2.fill(new Rectangle2D.Float(0, bw + lw, LW.getFloat(), r.height - (bw + lw) * 2));
          }

          g2.setColor(getArrowButtonForegroundColor(comboBox.isEnabled()));
          g2.fill(getArrowShape(this));
        } finally {
          g2.dispose();
        }
      }

      @Override
      public Dimension getPreferredSize() {
        return getArrowButtonPreferredSize(comboBox);
      }
    };
    button.setBorder(JBUI.Borders.empty());
    button.setOpaque(false);
    return button;
  }

  @SuppressWarnings("unused")
  @Deprecated
  protected Color getArrowButtonFillColor(Color defaultColor) {
    return getArrowButtonBackgroundColor(comboBox.isEnabled(), comboBox.isEditable());
  }

  @NotNull
  static Dimension getArrowButtonPreferredSize(@Nullable JComboBox comboBox) {
    Insets i = comboBox != null ? comboBox.getInsets() : getDefaultComboBoxInsets();
    int height = (isCompact(comboBox) ? COMPACT_HEIGHT.get() : MINIMUM_HEIGHT.get()) + i.top + i.bottom;
    return new Dimension(ARROW_BUTTON_WIDTH.get() + i.left, height);
  }

  static Shape getArrowShape(Component button) {
    Rectangle r = new Rectangle(button.getSize());
    JBInsets.removeFrom(r, JBUI.insets(1, 0, 1, 1));

    int tW = JBUI.scale(9);
    int tH = JBUI.scale(5);
    int xU = (r.width - tW) / 2 - JBUI.scale(1);
    int yU = (r.height - tH) / 2 + JBUI.scale(1);

    Path2D path = new Path2D.Float();
    path.moveTo(xU, yU);
    path.lineTo(xU + tW, yU);
    path.lineTo(xU + tW/2.0f, yU + tH);
    path.lineTo(xU, yU);
    path.closePath();
    return path;
  }

  @NotNull
  private static JBInsets getDefaultComboBoxInsets() {
    return JBUI.insets(3);
  }

  @Override
  protected Insets getInsets() {
    Object insets = comboBox.getClientProperty("JComboBox.insets");
    if (insets instanceof Insets) {
      return (Insets)insets;
    }

    return getDefaultComboBoxInsets().asUIResource();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Container parent = c.getParent();
    if (parent != null) {
      g.setColor(isTableCellEditor(c) && editor != null ? editor.getBackground() : parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(c.getSize());
    JBInsets.removeFrom(r, JBUI.insets(1));

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      float bw = BW.getFloat();
      float arc = COMPONENT_ARC.getFloat();

      boolean editable = comboBox.isEnabled() && editor != null && comboBox.isEditable();
      g2.setColor(editable ? editor.getBackground() : comboBox.isEnabled() ? NON_EDITABLE_BACKGROUND : UIUtil.getPanelBackground());

      g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
    } finally {
      g2.dispose();
    }

    if (!comboBox.isEditable()) {
      checkFocus();
      paintCurrentValue(g, rectangleForCurrentValue(), hasFocus);
    }
  }

  protected static boolean isTableCellEditor(JComponent c) {
    return Boolean.TRUE.equals(c.getClientProperty("JComboBox.isTableCellEditor")) || c.getParent() instanceof JTable;
  }

  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    ListCellRenderer renderer = comboBox.getRenderer();
    @SuppressWarnings("unchecked") Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);

    c.setFont(comboBox.getFont());
    c.setBackground(comboBox.isEnabled() ? NON_EDITABLE_BACKGROUND : UIUtil.getPanelBackground());

    if (hasFocus && !isPopupVisible(comboBox)) {
      c.setForeground(listBox.getForeground());
    } else {
      c.setForeground(comboBox.isEnabled() ? comboBox.getForeground() : JBColor.namedColor("ComboBox.disabledForeground", comboBox.getForeground()));
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

    // Reset fixed columns amount set to 9 by default
    if (comboBoxEditor instanceof BasicComboBoxEditor) {
      JTextField tf = (JTextField)comboBoxEditor.getEditorComponent();
      tf.setColumns(0);
    }

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
    if (!(c instanceof JComponent)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      checkFocus();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Rectangle r = new Rectangle(x, y, width, height);
      JBInsets.removeFrom(r, JBUI.insets(1));

      g2.translate(r.x, r.y);

      float lw = LW.getFloat();
      float bw = BW.getFloat();
      float arc = COMPONENT_ARC.getFloat();

      Object op = comboBox.getClientProperty("JComponent.outline");
      if (op != null) {
        paintOutlineBorder(g2, r.width, r.height, arc, true, hasFocus, Outline.valueOf(op.toString()));
      } else {
        if (hasFocus) {
          paintOutlineBorder(g2, r.width, r.height, arc, true, true, Outline.focus);
        }

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc), false);
        border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc - lw, arc - lw), false);

        g2.setColor(getOutlineColor(c.isEnabled(), hasFocus));
        g2.fill(border);
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

  protected Dimension getSizeWithButton(Dimension size, Dimension editorSize) {
    Insets i = getInsets();
    Dimension abSize = arrowButton.getPreferredSize();

    if (isCompact(comboBox) && size != null) {
      JBInsets.removeFrom(size, padding); // don't count paddings in compact mode
    }

    int editorHeight = editorSize != null ? editorSize.height + i.top + i.bottom: 0;
    int editorWidth = editorSize != null ? editorSize.width + i.left + padding.left + padding.right : 0;
    editorWidth = Math.max(editorWidth, MINIMUM_WIDTH.get() + i.left);

    int width = size != null ? size.width : 0;
    int height = size != null ? size.height : 0;

    width = Math.max(editorWidth + abSize.width, width + padding.left);
    height = Math.max(Math.max(editorHeight, Math.max(abSize.height, height)),
                          (isCompact(comboBox) ? COMPACT_HEIGHT.get() : MINIMUM_HEIGHT.get()) + i.top + i.bottom);

    return new Dimension(width, height);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getSizeWithButton(super.getPreferredSize(c), editor != null ? editor.getPreferredSize() : null);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getSizeWithButton(super.getMinimumSize(c), editor != null ? editor.getMinimumSize() : null);
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
          Color c = UIManager.getColor(comboBox.isEnabled() ? "TextField.background" : "ComboBox.disabledBackground");
          etf.setBackground(c);
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
      } else {
        er.height = eps.height;
      }
      editor.setBounds(er);
    }
  }

  protected Rectangle rectangleForCurrentValue() {
    Rectangle rect = super.rectangleForCurrentValue();
    JBInsets.removeFrom(rect, padding);
    rect.width += comboBox.isEditable() ? 0: padding.right;
    return rect;
  }

  // Wide popup that uses preferred size
  protected static class CustomComboPopup extends BasicComboPopup {
    public CustomComboPopup(JComboBox combo) {
      super(combo);
    }

    @Override
    public void show(Component invoker, int x, int y) {
      if (comboBox instanceof ComboBoxWithWidePopup) {
        Dimension popupSize = comboBox.getSize();
        int minPopupWidth = ((ComboBoxWithWidePopup)comboBox).getMinimumPopupWidth();
        Insets insets = getInsets();

        popupSize.width = Math.max(popupSize.width, minPopupWidth);
        popupSize.setSize(popupSize.width - (insets.right + insets.left), getPopupHeightForRowCount(comboBox.getMaximumRowCount()));

        scroller.setMaximumSize(popupSize);
        scroller.setPreferredSize(popupSize);
        scroller.setMinimumSize(popupSize);

        list.revalidate();
      }
      super.show(invoker, x, y);
    }
  }
}