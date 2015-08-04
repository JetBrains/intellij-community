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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJComboBoxUI extends BasicComboBoxUI {
  private static final Icon COMBOBOX = DarculaLaf.loadIcon("comboboxMac.png");
  private static final Icon COMBOBOX_LEFT = DarculaLaf.loadIcon("comboboxLeft.png");
  private static final Icon COMBOBOX_TOP_BOTTOM = DarculaLaf.loadIcon("comboboxTopBottom.png");
  private static final Icon COMBOBOX_FOCUSED = DarculaLaf.loadIcon("comboboxMacFocused.png");
  private static final Icon COMBOBOX_LEFT_FOCUSED = DarculaLaf.loadIcon("comboboxLeftFocused.png");
  private static final Icon COMBOBOX_TOP_BOTTOM_FOCUSED = DarculaLaf.loadIcon("comboboxTopBottomFocused.png");
  private static final Icon COMBOBOX_TOP_BOTTOM_DISABLED = DarculaLaf.loadIcon("comboboxTopBottomDisabled.png");
  private static final Icon COMBOBOX_LEFT_DISABLED = DarculaLaf.loadIcon("comboboxLeftDisabled.png");
  private static final Icon COMBOBOX_DISABLED = DarculaLaf.loadIcon("comboboxMacDisabledRight.png");
  private final JComboBox myComboBox;

  public MacIntelliJComboBoxUI(JComboBox comboBox) {
    myComboBox = comboBox;
    currentValuePane = new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        c.setBackground(myComboBox.isEnabled() ? Gray.xFF : Gray.xF6);
        super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
      }
    };
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJComboBoxUI((JComboBox)c);
  }

  @Override
  protected JButton createArrowButton() {
    final Color bg = myComboBox.getBackground();
    final Color fg = myComboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {
      @Override
      public void paint(Graphics g2) {
        Icon icon = myComboBox.isEnabled() ? myComboBox.hasFocus() ? COMBOBOX_FOCUSED : COMBOBOX : COMBOBOX_DISABLED;
        icon.paintIcon(this, g2, 0, 0);
      }

      @Override
      public Dimension getPreferredSize() {
        return JBUI.size(COMBOBOX.getIconWidth(), COMBOBOX.getIconHeight());
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getSizeWithIcon(super.getMinimumSize(c));
  }

  private static Dimension getSizeWithIcon(Dimension d) {
    return new Dimension(Math.max(d.width + 7, COMBOBOX.getIconWidth()), Math.max(d.height, COMBOBOX.getIconHeight()));
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getSizeWithIcon(super.getPreferredSize(c));
  }


  @Override
  protected ComboBoxEditor createEditor() {
      final ComboBoxEditor comboBoxEditor = new BasicComboBoxEditor.UIResource() {
        @Override
        protected JTextField createEditorComponent() {
          return new JTextField() {
            {
              setOpaque(false);
              setBorder(JBUI.Borders.empty(1, 0));
            }

            public void setText(String s) {
              if (getText().equals(s)) {
                return;
              }
              super.setText(s);
            }

            @Override
            public Dimension getPreferredSize() {
              Dimension size = super.getPreferredSize();
              return new Dimension(size.width, COMBOBOX.getIconHeight() - 6);
            }
          };
        }
      };
      if (comboBoxEditor.getEditorComponent() != null) {
        comboBoxEditor.getEditorComponent().addKeyListener(new KeyAdapter() {
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
        });
        comboBoxEditor.getEditorComponent().addFocusListener(new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            update();
          }

          @Override
          public void focusLost(FocusEvent e) {
            update();
          }

          void update() {
            if (comboBox != null) {
              comboBox.revalidate();
              comboBox.repaint();
            }
          }
        });
      }
      return comboBoxEditor;
    }

  @Override
  protected Rectangle rectangleForCurrentValue() {
    Rectangle rect = super.rectangleForCurrentValue();
    rect.height=Math.min(rect.height, COMBOBOX.getIconHeight()-8);
    rect.y+=4;
    rect.x+=8;
    rect.width-=8;
    return rect;
  }

  @Override
  protected Dimension getDefaultSize() {
    return super.getDefaultSize();
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return new LayoutManager() {
      @Override
      public void addLayoutComponent(String name, Component comp) {

      }

      @Override
      public void removeLayoutComponent(Component comp) {

      }

      @Override
      public Dimension preferredLayoutSize(Container parent) {
        return parent.getPreferredSize();
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        return parent.getMinimumSize();
      }

      @Override
      public void layoutContainer(Container parent) {
        JComboBox cb = (JComboBox)parent;
        int width = cb.getWidth();
        int height = cb.getHeight();

        Insets insets = getInsets();
        int buttonHeight = height - (insets.top + insets.bottom);
        int buttonWidth = COMBOBOX.getIconWidth();
        if (arrowButton != null) {
          Insets arrowInsets = arrowButton.getInsets();
          buttonWidth = arrowButton.getPreferredSize().width + arrowInsets.left + arrowInsets.right;
        }
        Rectangle cvb;

        if (arrowButton != null) {
            arrowButton.setBounds(width - (insets.right + buttonWidth),
                                  insets.top, buttonWidth, buttonHeight);
        }
        if ( editor != null ) {
          cvb = rectangleForCurrentValue();
          editor.setBounds(cvb);
        }

      }
    };
  }

  @Override
  public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
    g.setColor(myComboBox.isEnabled() ? Gray.xFF : Gray.xF6);
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
  }

  public void paintCurrentValue(Graphics g,Rectangle bounds,boolean hasFocus) {
    super.paintCurrentValue(g, bounds, comboBox.isPopupVisible());
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Rectangle r = arrowButton.getBounds();
    int stop = r.x;
    Insets clip = getInsets();
    Graphics gg = g.create(clip.left, r.y, stop - clip.left, COMBOBOX.getIconHeight());
    boolean enabled = c.isEnabled();
    boolean hasFocus = c.hasFocus();
    Icon icon = enabled ? hasFocus ? COMBOBOX_LEFT_FOCUSED : COMBOBOX_LEFT : COMBOBOX_LEFT_DISABLED;
    icon.paintIcon(c,gg,0,0);
    int x = icon.getIconWidth();
    icon = enabled ? hasFocus ? COMBOBOX_TOP_BOTTOM_FOCUSED : COMBOBOX_TOP_BOTTOM : COMBOBOX_TOP_BOTTOM_DISABLED;
    while (x < stop) {
      icon.paintIcon(c, gg, x, 0);
      x+=icon.getIconWidth();
    }
    gg.dispose();
    icon = enabled ? hasFocus ? COMBOBOX_FOCUSED : COMBOBOX : COMBOBOX_DISABLED;
    icon.paintIcon(c, g, r.x, r.y);

    if ( !comboBox.isEditable() ) {
      paintCurrentValue(g, rectangleForCurrentValue(), false);
    }
  }
}
