// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaComboBoxUI extends BasicComboBoxUI implements Border, ErrorBorderCapable {
  public static final Key<Boolean> PAINT_VERTICAL_LINE = Key.create("PAINT_VERTICAL_LINE");
  @SuppressWarnings("UnregisteredNamedColor")
  private static final Color NON_EDITABLE_BACKGROUND = JBColor.namedColor("ComboBox.nonEditableBackground",
                                                                          JBColor.namedColor("ComboBox.darcula.nonEditableBackground", new JBColor(0xfcfcfc, 0x3c3f41)));

  private float myArc = COMPONENT_ARC.getFloat();
  private Insets myBorderCompensation = JBUI.insets(1);
  private boolean myPaintArrowButton = true;

  public DarculaComboBoxUI() {}

  public DarculaComboBoxUI(float arc,
                           Insets borderCompensation,
                           boolean paintArrowButton) {
    myArc = arc;
    myBorderCompensation = borderCompensation;
    myPaintArrowButton = paintArrowButton;
  }

  @SuppressWarnings("unused")
  @Deprecated
  public DarculaComboBoxUI(JComboBox c) {}

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaComboBoxUI();
  }

  private KeyListener editorKeyListener;
  private FocusListener editorFocusListener;
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

  @Override
  protected void installListeners() {
    super.installListeners();

    propertyListener = createPropertyListener();
    comboBox.addPropertyChangeListener(propertyListener);
  }

  @Override
  public void uninstallListeners() {
    super.uninstallListeners();

    if (propertyListener != null) {
      comboBox.removePropertyChangeListener(propertyListener);
      propertyListener = null;
    }
  }

  public static boolean hasSwingPopup(JComponent component) {
    return component.getClientProperty(DarculaJBPopupComboPopup.CLIENT_PROP) == null;
  }

  @Override
  protected ComboPopup createPopup() {
    return hasSwingPopup(comboBox) ? new CustomComboPopup(comboBox) : new DarculaJBPopupComboPopup<>(comboBox);
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

  @Override
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

          if (myPaintArrowButton) {
            float bw = BW.getFloat();
            float lw = LW.getFloat();
            float arc = myArc;
            arc = arc > bw + lw ? arc - bw - lw : 0.0f;

            Path2D innerShape = new Path2D.Float();
            innerShape.moveTo(lw, bw + lw);
            innerShape.lineTo(r.width - bw - lw - arc, bw + lw);
            innerShape.quadTo(r.width - bw - lw, bw + lw, r.width - bw - lw, bw + lw + arc);
            innerShape.lineTo(r.width - bw - lw, r.height - bw - lw - arc);
            innerShape.quadTo(r.width - bw - lw, r.height - bw - lw, r.width - bw - lw - arc, r.height - bw - lw);
            innerShape.lineTo(lw, r.height - bw - lw);
            innerShape.closePath();

            g2.setColor(JBUI.CurrentTheme.Arrow.backgroundColor(comboBox.isEnabled(), comboBox.isEditable()));
            g2.fill(innerShape);

            // Paint vertical line
            if (comboBox.isEditable() || ClientProperty.isTrue(comboBox, PAINT_VERTICAL_LINE)) {
              g2.setColor(getOutlineColor(comboBox.isEnabled(), false));
              g2.fill(new Rectangle2D.Float(0, bw + lw, LW.getFloat(), r.height - (bw + lw) * 2));
            }
          }

          paintArrow(g2, this);
        }
        finally {
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

  protected void paintArrow(Graphics2D g2, JButton btn) {
    g2.setColor(JBUI.CurrentTheme.Arrow.foregroundColor(comboBox.isEnabled()));
    g2.fill(getArrowShape(btn));
  }

  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected Color getArrowButtonFillColor(Color defaultColor) {
    return JBUI.CurrentTheme.Arrow.backgroundColor(comboBox.isEnabled(), comboBox.isEditable());
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

    int tW = JBUIScale.scale(9);
    int tH = JBUIScale.scale(5);
    int xU = (r.width - tW) / 2 - JBUIScale.scale(1);
    int yU = (r.height - tH) / 2 + JBUIScale.scale(1);

    Path2D path = new Path2D.Float();
    path.moveTo(xU, yU);
    path.lineTo(xU + tW, yU);
    path.lineTo(xU + tW / 2.0f, yU + tH);
    path.lineTo(xU, yU);
    path.closePath();
    return path;
  }

  @NotNull
  private static JBInsets getDefaultComboBoxInsets() {
    return JBUI.insets(3);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Container parent = c.getParent();
    if (parent != null && c.isOpaque()) {
      g.setColor(DarculaUIUtil.isTableCellEditor(c) && editor != null ? editor.getBackground() : parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(c.getSize());
    JBInsets.removeFrom(r, myBorderCompensation);

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      float bw = isBorderless(c) ? LW.getFloat() : BW.getFloat();

      g2.setColor(getBackgroundColor());
      g2.fill(getOuterShape(r, bw, myArc));
    }
    finally {
      g2.dispose();
    }

    if (!comboBox.isEditable()) {
      checkFocus();
      paintCurrentValue(g, rectangleForCurrentValue(), hasFocus);
    }
    // remove staled renderers from hierarchy
    // see BasicTreeUI#paint
    // see BasicListUI#paintImpl
    // see BasicTableUI#paintCells
    currentValuePane.removeAll();
  }

  private Color getBackgroundColor() {
    Color bg = comboBox.getBackground();
    if (comboBox.isEditable() && editor != null) {
      return comboBox.isEnabled() ? editor.getBackground() :
             comboBox.isBackgroundSet() && !(bg instanceof UIResource) ? bg : UIUtil.getComboBoxDisabledBackground();
    }
    else {
      Object value = comboBox.getSelectedItem();
      Color coloredItemColor = value instanceof ColoredItem ? ((ColoredItem)value).getColor(): null;
      return ObjectUtils.notNull(coloredItemColor,
              comboBox.isBackgroundSet() && !(bg instanceof UIResource) ? bg :
              comboBox.isEnabled() ? NON_EDITABLE_BACKGROUND : UIUtil.getComboBoxDisabledBackground());
    }
  }

  /**
   * @deprecated Use {@link DarculaUIUtil#isTableCellEditor(Component)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected static boolean isTableCellEditor(JComponent c) {
    return DarculaUIUtil.isTableCellEditor(c);
  }

  @Override
  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    ListCellRenderer<Object> renderer = comboBox.getRenderer();
    Object value = comboBox.getSelectedItem();
    Component c = renderer.getListCellRendererComponent(listBox, value, -1, false, false);

    c.setFont(comboBox.getFont());
    c.setBackground(getBackgroundColor());

    if (hasFocus && !isPopupVisible(comboBox)) {
      c.setForeground(listBox.getForeground());
    }
    else {
      c.setForeground(comboBox.isEnabled() ? comboBox.getForeground() :
                      JBColor.namedColor("ComboBox.disabledForeground", comboBox.getForeground()));
    }

    // paint selection in table-cell-editor mode correctly
    boolean changeOpaque = c instanceof JComponent && DarculaUIUtil.isTableCellEditor(comboBox) && c.isOpaque();
    if (changeOpaque) {
      ((JComponent)c).setOpaque(false);
    }

    boolean shouldValidate = false;
    if (c instanceof JPanel) {
      shouldValidate = true;
    }

    Rectangle r = new Rectangle(bounds);

    Icon icon = null;
    Insets iPad = null;
    Border border = null;
    boolean enabled = true;
    if (c instanceof SimpleColoredComponent) {
      SimpleColoredComponent cc = (SimpleColoredComponent)c;
      iPad = cc.getIpad();
      border = cc.getBorder();
      enabled = cc.isEnabled();
      cc.setBorder(JBUI.Borders.empty());
      cc.setIpad(JBInsets.emptyInsets());
      cc.setEnabled(comboBox.isEnabled());
      icon = cc.getIcon();
      if (!cc.isIconOnTheRight()) {
        cc.setIcon(OffsetIcon.getOriginalIcon(icon));
      }
    }
    else if (c instanceof JLabel) {
      JLabel cc = (JLabel)c;
      border = cc.getBorder();
      cc.setBorder(JBUI.Borders.empty());
      icon = cc.getIcon();
      cc.setIcon(OffsetIcon.getOriginalIcon(icon));

      // the following trimMiddle approach is not good for smooth resizing:
      // the text jumps as more or less space becomes available.
      // a proper text layout algorithm on painting in DarculaLabelUI can fix that.
      String text = cc.getText();
      int maxWidth = bounds.width - (padding == null || StartupUiUtil.isUnderDarcula() ? 0 : padding.right);
      if (StringUtil.isNotEmpty(text) && cc.getPreferredSize().width > maxWidth) {
        int max0 = ObjectUtils.binarySearch(7, text.length() - 1, idx -> {
          cc.setText(StringUtil.trimMiddle(text, idx));
          return Comparing.compare(cc.getPreferredSize().width, maxWidth);
        });
        int max = max0 < 0 ? -max0 - 2 : max0;
        if (max > 7 && max < text.length()) {
          cc.setText(StringUtil.trimMiddle(text, max));
        }
      }
    }
    else if (c instanceof JComponent) {
      JComponent cc = (JComponent)c;
      border = cc.getBorder();
      cc.setBorder(JBUI.Borders.empty());
    }

    currentValuePane.paintComponent(g, c, comboBox, r.x, r.y, r.width, r.height, shouldValidate);

    // return opaque for combobox popup items painting
    if (changeOpaque) {
      ((JComponent)c).setOpaque(true);
    }

    if (c instanceof SimpleColoredComponent) {
      SimpleColoredComponent cc = (SimpleColoredComponent)c;
      cc.setIpad(iPad);
      cc.setIcon(icon);
      cc.setBorder(border);
      cc.setEnabled(enabled);
    }
    else if (c instanceof JLabel) {
      JLabel cc = (JLabel)c;
      cc.setBorder(border);
      cc.setIcon(icon);
    }
    else if (c instanceof JComponent) {
      JComponent cc = (JComponent)c;
      cc.setBorder(border);
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
    float bw = BW.getFloat();
    Rectangle r = new Rectangle(x, y, width, height);

    try {
      checkFocus();
      if (!DarculaUIUtil.isTableCellEditor(c)) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        JBInsets.removeFrom(r, myBorderCompensation);
        g2.translate(r.x, r.y);

        float lw = LW.getFloat();

        Object op = comboBox.getClientProperty("JComponent.outline");
        if (comboBox.isEnabled() && op != null) {
          paintOutlineBorder(g2, r.width, r.height, myArc, true, hasFocus, Outline.valueOf(op.toString()));
        }
        else {
          if (hasFocus && !isBorderless(c)) {
            paintOutlineBorder(g2, r.width, r.height, myArc, true, true, Outline.focus);
          }

          paintBorder(c, g2, isBorderless(c) ? lw : bw, r, lw, myArc);
        }
      }
      else {
        paintCellEditorBorder(g2, c, r, hasFocus);
      }
    }
    finally {
      g2.dispose();
    }
  }

  protected void paintBorder(Component c, Graphics2D g2, float bw, Rectangle r, float lw, float arc) {
    Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    border.append(getOuterShape(r, bw, arc), false);

    arc = arc > lw ? arc - lw : 0.0f;
    border.append(getInnerShape(r, bw, lw, arc), false);

    g2.setColor(getOutlineColor(c.isEnabled(), hasFocus));
    g2.fill(border);
  }

  protected RectangularShape getOuterShape(Rectangle r, float bw, float arc) {
    return new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc);
  }

  protected RectangularShape getInnerShape(Rectangle r, float bw, float lw, float arc) {
    return new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc, arc);
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
    return DarculaUIUtil.isTableCellEditor(c) || isCompact(c) ? JBInsets.create(2, 3) :
           isBorderless(c) ? JBInsets.emptyInsets() : getDefaultComboBoxInsets();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  protected Dimension getSizeWithButton(Dimension size, Dimension editorSize) {
    Insets i = getInsets();
    Dimension abSize = getArrowButtonPreferredSize(comboBox);

    if (isCompact(comboBox) && size != null) {
      JBInsets.removeFrom(size, padding); // don't count paddings in compact mode
    }

    int editorHeight = editorSize != null ? editorSize.height + i.top + i.bottom : 0;
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
    return getSizeWithButton(super.getMinimumSize(c), editor != null ? editor.getPreferredSize() : null);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension minSize = super.getMinimumSize(c);
    Insets i = c.getInsets();
    minSize.width = MINIMUM_WIDTH.get() + ARROW_BUTTON_WIDTH.get() + i.left + i.right;
    return getSizeWithButton(minSize, editor != null ? editor.getMinimumSize() : null);
  }

  @Override
  protected void configureEditor() {
    super.configureEditor();

    installEditorKeyListener(comboBox.getEditor());
    if (editor instanceof JComponent) {
      JComponent jEditor = (JComponent)editor;
      jEditor.setOpaque(false);
      jEditor.setBorder(JBUI.Borders.empty());

      editorFocusListener = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          update();
        }

        @Override
        public void focusLost(FocusEvent e) {
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
      }
      else {
        EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
        if (etf != null) {
          etf.addFocusListener(editorFocusListener);
          Color c = UIManager.getColor(comboBox.isEnabled() ? "TextField.background" : "ComboBox.disabledBackground");
          etf.setBackground(c);
        }
      }
    }

    // BasicComboboxUI sets focusability depending on the combobox focusability.
    // JPanel usually is unfocusable and uneditable.
    // It could be set as an editor when people want to have a composite component as an editor.
    // In such cases we should restore unfocusable state for panels.
    if (editor instanceof JPanel) {
      editor.setFocusable(false);
    }
  }

  @Override
  protected void unconfigureEditor() {
    super.unconfigureEditor();

    if (editorKeyListener != null) {
      editor.removeKeyListener(editorKeyListener);
    }

    if (editor instanceof JTextComponent) {
      if (editorFocusListener != null) {
        editor.removeFocusListener(editorFocusListener);
      }
    }
    else {
      EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
      if (etf != null) {
        if (editorFocusListener != null) {
          etf.removeFocusListener(editorFocusListener);
        }
      }
    }
  }

  @Override
  public boolean isFocusTraversable(JComboBox<?> c) {
    return !comboBox.isEditable() || !(editor instanceof ComboBoxCompositeEditor && ((ComboBoxCompositeEditor<?, ?>)editor).isEditable());
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
          }
          else {
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
      }
      er.height = eps.height;
      editor.setBounds(er);
    }
  }

  @Override
  protected Rectangle rectangleForCurrentValue() {
    Rectangle rect = new Rectangle(comboBox.getSize());
    Insets i = getInsets();

    JBInsets.removeFrom(rect, i);
    rect.width -= arrowButton != null ? (arrowButton.getWidth() - i.left) : rect.height;
    JBInsets.removeFrom(rect, padding);

    rect.width += comboBox.isEditable() ? 0 : padding.right;
    return rect;
  }

  // Wide popup that uses preferred size
  protected static class CustomComboPopup extends BasicComboPopup {
    public CustomComboPopup(JComboBox combo) {
      super(combo);
    }

    @Override
    protected void configurePopup() {
      super.configurePopup();
      Border border = UIManager.getBorder("ComboPopup.border");
      setBorder(border != null ? border :
                SystemInfo.isMac ? JBUI.Borders.empty() :
                IdeBorderFactory.createBorder());
      putClientProperty("JComboBox.isCellEditor", DarculaUIUtil.isTableCellEditor(comboBox));
    }

    @Override
    public void updateUI() {
      setUI(new BasicPopupMenuUI() {
        @Override
        public void uninstallDefaults() {}

        @Override
        public void installDefaults() {
          if (popupMenu.getLayout() == null || popupMenu.getLayout() instanceof UIResource) {
            popupMenu.setLayout(new DefaultMenuLayout(popupMenu, BoxLayout.Y_AXIS));
          }

          popupMenu.setOpaque(true);
          LookAndFeel.installColorsAndFont(popupMenu, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
        }
      });
    }

    @Override
    public void show(Component invoker, int x, int y) {
      if (comboBox instanceof ComboBoxWithWidePopup) {
        Dimension popupSize = comboBox.getSize();
        int minPopupWidth = ((ComboBoxWithWidePopup<?>)comboBox).getMinimumPopupWidth();
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

    @Override
    protected void configureList() {
      super.configureList();
      //noinspection unchecked
      list.setCellRenderer(new MyDelegateRenderer());
      list.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);
    }

    protected void customizeListRendererComponent(JComponent component) {
      component.setBorder(JBUI.Borders.empty(2, 8));
    }

    @Override
    protected PropertyChangeListener createPropertyChangeListener() {
      PropertyChangeListener listener = super.createPropertyChangeListener();
      return new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          listener.propertyChange(evt);
          if ("renderer".equals(evt.getPropertyName())) {
            if (!(list.getCellRenderer() instanceof MyDelegateRenderer)) {
              //noinspection unchecked
              list.setCellRenderer(new MyDelegateRenderer());
            }
          }
        }
      };
    }

    @Override
    protected int getPopupHeightForRowCount(int maxRowCount) {
      int minRowCount = Math.min(maxRowCount, comboBox.getItemCount());
      int height = 0;
      ListCellRenderer<Object> renderer = list.getCellRenderer();

      for (int i = 0; i < minRowCount; i++) {
        Object value = list.getModel().getElementAt(i);
        Component c = renderer.getListCellRendererComponent(list, value, i, false, false);

        // The whole method is copied from the parent class except for the following line
        // that adjusts the minimum row height of a list cell.
        // See WideSelectionListUI.updateLayoutState
        height += UIUtil.updateListRowHeight(c.getPreferredSize()).height;
      }

      if (height == 0) {
        height = comboBox.getHeight();
      }

      Border border = scroller.getViewportBorder();
      if (border != null) {
        Insets insets = border.getBorderInsets(null);
        height += insets.top + insets.bottom;
      }

      border = scroller.getBorder();
      if (border != null) {
        Insets insets = border.getBorderInsets(null);
        height += insets.top + insets.bottom;
      }

      return height;
    }

    private class MyDelegateRenderer implements ListCellRenderer {
      @Override
      public Component getListCellRendererComponent(JList list,
                                                    Object value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        //noinspection unchecked
        Component component = comboBox.getRenderer().getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (component instanceof JComponent) {
          customizeListRendererComponent((JComponent)component);
        }
        return component;
      }
    }
  }
}
