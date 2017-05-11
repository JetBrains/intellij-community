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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.EditorTextField;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJComboBoxUI extends DarculaComboBoxUI {
  private static final String HOVER_PROPERTY = "JComboBox.mouseHover";
  private static final String PRESSED_PROPERTY = "JComboBox.mousePressed";

  private static final Border DEFAULT_EDITOR_BORDER = JBUI.Borders.empty(1, 0);
  private static final Dimension BUTTON_SIZE = JBUI.size(20, 22);

  private MouseListener mouseListener;

  private MouseListener buttonReleaseListener;
  private MouseListener buttonHoverListener;
  private PropertyChangeListener propertyListener;

  private MouseListener editorHoverListener;
  private KeyListener   editorKeyListener;
  private FocusListener editorFocusListener;

  public WinIntelliJComboBoxUI(JComboBox comboBox) {
    super(comboBox);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJComboBoxUI((JComboBox)c);
  }

  @Override protected void installListeners() {
    super.installListeners();

    if (!comboBox.isEditable()) {
      comboBox.addMouseListener(mouseListener = new ComboBoxMouseListener());
    }

    propertyListener = (evt) -> {
      if("enabled".equals(evt.getPropertyName())) {
        setEditorTextFieldBackground();
      } else if ("editable".equals(evt.getPropertyName())) {
        if (evt.getNewValue() == Boolean.TRUE) {
          comboBox.removeMouseListener(mouseListener);
        } else {
          comboBox.addMouseListener(mouseListener);
        }
      }
    };

    comboBox.addPropertyChangeListener(propertyListener);
  }

  @Override public void uninstallListeners() {
    super.uninstallListeners();
    comboBox.removeMouseListener(mouseListener);

    if (propertyListener != null) {
      comboBox.removePropertyChangeListener(propertyListener);
      propertyListener = null;
    }
  }

  @Override public void paint(Graphics g, JComponent c) {
    int w = c.getWidth();
    int h = c.getHeight();

    if (c.isOpaque()) {
      Container parent = c.getParent();
      if (parent != null) {
        g.setColor(isTableCellEditor(c) && editor != null ? editor.getBackground() : parent.getBackground());
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
      }
    }

    int bw = JBUI.scale(1);

    g.setColor(getComboBackground());
    g.fillRect(bw, bw, w-2*bw, h-2*bw);

    if (!isTableCellEditor(c)) {
      paintBorder(c, g, 0, 0, w, h);
      hasFocus = comboBox.hasFocus();
    }

    if (!comboBox.isEditable()) {
      Rectangle r = rectangleForCurrentValue();
      paintCurrentValue(g, r, hasFocus);
    }
  }

  @Override protected Rectangle rectangleForCurrentValue() {
    int width = comboBox.getWidth();
    int height = comboBox.getHeight();
    Insets insets = getInsets();
    int buttonSize = height - (insets.top + insets.bottom);
    if ( arrowButton != null ) {
      buttonSize = arrowButton.getWidth();
    }

    Rectangle rect = (comboBox.getComponentOrientation().isLeftToRight()) ?
      new Rectangle(insets.left, insets.top,
                           width - (insets.left + insets.right + buttonSize),
                           height - (insets.top + insets.bottom)) :
      new Rectangle(insets.left + buttonSize, insets.top,
                           width - (insets.left + insets.right + buttonSize),
                           height - (insets.top + insets.bottom));

    if (editor instanceof JComponent) {
      JBInsets.removeFrom(rect, ((JComponent)editor).getInsets());
    }

    return rect;
  }


  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    @SuppressWarnings("unchecked")
    ListCellRenderer<Object> renderer = comboBox.getRenderer();
    Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);

    c.setBackground(getComboBackground());
    c.setFont(comboBox.getFont());
    c.setForeground(comboBox.isEnabled() ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));

    // paint selection in table-cell-editor mode correctly
    boolean changeOpaque = c instanceof JComponent && isTableCellEditor(comboBox) && c.isOpaque();
    if (changeOpaque) {
      ((JComponent)c).setOpaque(false);
    }

    Rectangle r = new Rectangle(bounds);
    Insets i = UIManager.getInsets("ComboBox.padding");
    if (i == null && c instanceof JComponent) {
      i = ((JComponent)c).getInsets();
    }

    JBInsets.removeFrom(r, i);

    currentValuePane.paintComponent(g, c, comboBox, r.x, r.y, r.width, r.height, c instanceof JPanel);

    // return opaque for combobox popup items painting
    if (changeOpaque) {
      ((JComponent)c).setOpaque(true);
    }
  }

  private Color getComboBackground() {
    if (comboBox.isEnabled() && comboBox.isEditable()) {
      return UIManager.getColor("TextField.background");
    } else if (!comboBox.isEnabled()) {
      return UIManager.getColor("Button.background");
    } else if (!comboBox.isEditable()) {
      if (isPressed()) {
        return UIManager.getColor("Button.intellij.native.pressedBackgroundColor");
      } else if (isHover()) {
        return UIManager.getColor("Button.intellij.native.focusedBackgroundColor");
      }
    }
    return UIManager.getColor("Button.background");
  }

  @Override
  protected JButton createArrowButton() {
    JButton button = new BasicArrowButton(SwingConstants.SOUTH) {
      @Override
      public Dimension getPreferredSize() {
        return BUTTON_SIZE;
      }

      @Override
      public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          int bw = JBUI.scale(1);

          Rectangle2D innerRect = new Rectangle2D.Double(bw, bw, getWidth() - bw*2, getHeight() - bw*2);
          if (comboBox.isEditable() && comboBox.isEnabled()) {
            if (isPressed()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.pressedBackgroundColor"));
            } else if (comboBox.hasFocus() || isHover()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.focusedBackgroundColor"));
            } else {
              g2.setColor(getComboBackground());
            }
          } else {
            g2.setColor(getComboBackground());
          }

          if (!comboBox.isEnabled()) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
          }

          g2.fill(innerRect);

          // paint border around button when combobox is editable
          if (comboBox.isEditable() && comboBox.isEnabled()) {
            Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            border.append(new Rectangle2D.Double(0, 0, getWidth(), getHeight()), false);
            border.append(innerRect, false);

            if (getModel().isPressed()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.pressedBorderColor"));
              g2.fill(border);
            } else if (comboBox.hasFocus() || isHover()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.focusedBorderColor"));
              g2.fill(border);
            }
          }

          IconUtil.paintInCenterOf(this, g2, MacIntelliJIconCache.getIcon("comboDropTriangle", false, false, isEnabled()));
        } finally {
          g2.dispose();
        }
      }
    };

    button.setOpaque(false);
    button.setBorder(JBUI.Borders.empty(1, 0, 1, 1));
    buttonReleaseListener = new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (!comboBox.isEditable()) {
          comboBox.repaint();
        }
      }
    };

    buttonHoverListener = new DarculaUIUtil.MouseHoverPropertyTrigger(comboBox, HOVER_PROPERTY);

    button.addMouseListener(buttonHoverListener);
    button.addMouseListener(buttonReleaseListener);
    return button;
  }

  @Override public void unconfigureArrowButton() {
    super.unconfigureArrowButton();
    if (arrowButton != null) {
      arrowButton.removeMouseListener(buttonReleaseListener);
      arrowButton.removeMouseListener(buttonHoverListener);
    }
  }


  @Override
  protected ComboBoxEditor createEditor() {
    ComboBoxEditor comboBoxEditor = new BasicComboBoxEditor.UIResource() {
      @Override
      protected JTextField createEditorComponent() {
        return new JTextField() {
          {
            setOpaque(false);
            setBorder(DEFAULT_EDITOR_BORDER);
          }

          public void setText(String s) {
            if (getText().equals(s)) {
              return;
            }
            super.setText(s);
          }

          @Override public Color getBackground() {
            return getComboBackground();
          }
          @Override public void setBorder(Border border) {}
          @Override public Border getBorder() {
            return DEFAULT_EDITOR_BORDER;
          }

          @Override
          public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, BUTTON_SIZE.height);
          }
        };
      }
    };

    Component ec = comboBoxEditor.getEditorComponent();
    if (ec != null) {
      editorKeyListener = new KeyAdapter() {
        @Override public void keyPressed(KeyEvent e) {
          process(e);
        }
        @Override public void keyReleased(KeyEvent e) {
          process(e);
        }

        private void process(KeyEvent e) {
          int code = e.getKeyCode();
          if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && e.getModifiers() == 0) {
            comboBox.dispatchEvent(e);
          }
        }
      };

      ec.addKeyListener(editorKeyListener);
    }
    return comboBoxEditor;
  }

  @Override protected void configureEditor() {
    if (editor != null) {
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

      editorHoverListener = new DarculaUIUtil.MouseHoverPropertyTrigger(comboBox, HOVER_PROPERTY);

      if (editor instanceof JTextComponent) {
        editor.addFocusListener(editorFocusListener);
        editor.addMouseListener(editorHoverListener);
      } else {
        EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
        if (etf != null) {
          etf.addFocusListener(editorFocusListener);
          etf.addMouseListener(editorHoverListener);
        }
      }

      ((JComponent)editor).setBorder(DEFAULT_EDITOR_BORDER);
      ((JComponent)editor).setOpaque(false);

      setEditorTextFieldBackground();
    }
  }

  private void setEditorTextFieldBackground() {
    EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
    if (etf != null && comboBox.isEditable()) {
      etf.setBackground(getComboBackground());
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

      if (editorHoverListener != null) {
        editor.removeMouseListener(editorHoverListener);
      }
    } else {
      EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
      if (etf != null) {
        if (editorHoverListener != null) {
          etf.removeFocusListener(editorFocusListener);
        }

        if (editorHoverListener != null) {
          etf.removeMouseListener(editorHoverListener);
        }
      }
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
      g2.translate(x, y);

      if (Registry.is("ide.inplace.errors.outline") && comboBox.getClientProperty("JComponent.error.outline") == Boolean.TRUE) {
        DarculaUIUtil.paintErrorBorder(g2, width, height, 0, true, hasFocus);
      } else if (comboBox.isEnabled()) {
        if (comboBox.isEditable()) {
          if (hasFocus) {
            g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
          } else {
            g2.setColor(UIManager.getColor(isEditorHover() ? "TextField.hoverBorderColor" : "TextField.borderColor"));
          }
        } else {
          if (isPressed()) {
            g2.setColor(UIManager.getColor("Button.intellij.native.pressedBorderColor"));
          } else if (isHover() || hasFocus) {
            g2.setColor(UIManager.getColor("Button.intellij.native.focusedBorderColor"));
          } else {
            g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
          }
        }
      } else {
        g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
      }


      int bw = JBUI.scale(1);
      Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      border.append(new Rectangle2D.Double(0, 0, width, height), false);
      border.append(new Rectangle2D.Double(bw, bw, width - bw*2, height - bw*2), false);
      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }

  private boolean isHover() {
    return comboBox != null && comboBox.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE ||
           arrowButton != null && arrowButton.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE;
  }

  private boolean isEditorHover() {
    JComponent ec = (JComponent)comboBox.getEditor().getEditorComponent();
    EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
    Editor editor = etf != null ? etf.getEditor() : null;

    return arrowButton != null && arrowButton.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE ||
           ec != null && ec.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE ||
           editor != null && editor.getContentComponent().getClientProperty(HOVER_PROPERTY) == Boolean.TRUE;
  }

  private boolean isPressed() {
    return comboBox != null && comboBox.getClientProperty(PRESSED_PROPERTY) == Boolean.TRUE ||
           arrowButton != null && arrowButton.getModel().isPressed();
  }

  @Override
  protected Insets getInsets() {
    return JBUI.insets(0, 5, 0, 0).asUIResource();
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return getInsets();
  }

  private Dimension getSizeWithIcon(Dimension d) {
    Insets i = comboBox.getInsets();
    int iconWidth = BUTTON_SIZE.width + i.right;
    int iconHeight = BUTTON_SIZE.height + i.top + i.bottom;
    return new Dimension(Math.max(d.width + 7, iconWidth), iconHeight);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getSizeWithIcon(super.getPreferredSize(c));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getSizeWithIcon(super.getMinimumSize(c));
  }

  private class ComboBoxMouseListener extends MouseAdapter {
    @Override public void mousePressed(MouseEvent e) {
      setPressedProperty(true);
    }

    @Override public void mouseReleased(MouseEvent e) {
      setPressedProperty(false);
    }

    private void setPressedProperty(boolean isPressed) {
      if (!comboBox.isEditable()) {
        comboBox.putClientProperty(PRESSED_PROPERTY, Boolean.valueOf(isPressed));
        comboBox.repaint();
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      setHoverProperty(true);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setHoverProperty(false);
    }

    private void setHoverProperty(boolean isHover) {
      comboBox.putClientProperty(HOVER_PROPERTY, Boolean.valueOf(isHover));
      comboBox.repaint();
    }
  }
}
