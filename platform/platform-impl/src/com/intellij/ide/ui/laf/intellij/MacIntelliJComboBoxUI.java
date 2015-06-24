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
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
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
  private final JComboBox myComboBox;

  public MacIntelliJComboBoxUI(JComboBox comboBox) {
    myComboBox = comboBox;
    currentValuePane = new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        c.setBackground(Color.WHITE);
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
        Icon icon = myComboBox.isEnabled() ? COMBOBOX : IconLoader.getDisabledIcon(COMBOBOX);
        icon.paintIcon(this, g2, 0, 0);
      }

      @Override
      public Dimension getPreferredSize() {
        return JBUI.size(COMBOBOX.getIconWidth(), COMBOBOX.getIconWidth());
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

  private Dimension getSizeWithIcon(Dimension d) {
    return new Dimension(Math.max(d.width + 6, COMBOBOX.getIconWidth()), Math.max(d.height, COMBOBOX.getIconHeight()));
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
              setBorder(new Border() {
                @Override
                public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                }

                @Override
                public Insets getBorderInsets(Component c) {
                  return JBUI.insets(2, 6, 2, 6);
                }

                @Override
                public boolean isBorderOpaque() {
                  return false;
                }
              });
            }

            public void setText(String s) {
              if (getText().equals(s)) {
                return;
              }
              super.setText(s);
            }

            public void setBorder(Border b) {
                super.setBorder(b);
            }

            @Override
            public Dimension getPreferredSize() {
              Dimension size = super.getPreferredSize();
              return new Dimension(size.width, COMBOBOX.getIconHeight() - 2);
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
    rect.height=Math.min(rect.height, COMBOBOX.getIconHeight());
    rect.y+=2;
    rect.x+=5;
    rect.height-=4;
    return rect;
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
        return null;
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        return null;
      }

      @Override
      public void layoutContainer(Container parent) {
        JComboBox cb = (JComboBox)parent;
        int width = cb.getWidth();
        int height = cb.getHeight();

        Insets insets = getInsets();
        int buttonHeight = height - (insets.top + insets.bottom);
        int buttonWidth = buttonHeight;
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
    g.setColor(Color.WHITE);
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
  }

  public void paintCurrentValue(Graphics g,Rectangle bounds,boolean hasFocus) {
    super.paintCurrentValue(g, bounds, comboBox.isPopupVisible());
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    super.paint(g, c);

    Rectangle r = arrowButton.getBounds();
    int stop = r.x;
    g.setClip(0,0, stop, COMBOBOX.getIconHeight());
    COMBOBOX_LEFT.paintIcon(c,g,0,r.y);
    int x = COMBOBOX_LEFT.getIconWidth();
    while (x < stop) {
      COMBOBOX_TOP_BOTTOM.paintIcon(c, g, x, r.y);
      x+=COMBOBOX_TOP_BOTTOM.getIconWidth();
    }
    if (UIUtil.isRetina()) {
      ((Graphics2D)g).scale(0.5d, 0.5d);
      g.setColor(Color.WHITE);
      g.drawLine(COMBOBOX_LEFT.getIconWidth() * 2, 3, stop * 2, 3);
      g.drawLine(COMBOBOX_LEFT.getIconWidth() * 2, 40, stop * 2, 40);
      ((Graphics2D)g).scale(2d, 2d);
    }
  }
}
