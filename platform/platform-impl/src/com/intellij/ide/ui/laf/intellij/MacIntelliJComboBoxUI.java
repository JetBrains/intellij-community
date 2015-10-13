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

import com.intellij.ui.Gray;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJComboBoxUI extends BasicComboBoxUI {
  private static final Icon DEFAULT_ICON = EmptyIcon.create(MacIntelliJIconCache.getIcon("comboRight"));
  private static final Border ourDefaultEditorBorder = JBUI.Borders.empty(1, 0);

  private final JComboBox myComboBox;
  private PropertyChangeListener myEditorChangeListener;
  private PropertyChangeListener myEditorBorderChangeListener;

  public MacIntelliJComboBoxUI(JComboBox comboBox) {
    myComboBox = comboBox;
    comboBox.setOpaque(false);
    currentValuePane = new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        c.setBackground(myComboBox.isEnabled() ? Gray.xFF : Gray.xF8);
        super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
      }
    };
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJComboBoxUI((JComboBox)c);
  }

  @Override
  public void installUI(final JComponent c) {
    super.installUI(c);
    myEditorBorderChangeListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        Object value = evt.getNewValue();
        if (value == ourDefaultEditorBorder) return;
        ComboBoxEditor editor = ((JComboBox)c).getEditor();
        if (editor != null) {
          Component component = editor.getEditorComponent();
          if (component instanceof JComponent) {
            ((JComponent)component).setBorder(ourDefaultEditorBorder);
          }
        }
      }
    };
    myEditorChangeListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        Object value = evt.getNewValue();
        Object oldEditor = evt.getOldValue();
        if (oldEditor instanceof ComboBoxEditor) {
          Component component = ((ComboBoxEditor)oldEditor).getEditorComponent();
          if (component instanceof JComponent) {
            component.removePropertyChangeListener("border", myEditorBorderChangeListener);
          }
        }
        if (value instanceof ComboBoxEditor) {
          Component component = ((ComboBoxEditor)value).getEditorComponent();
          if (component instanceof JComponent) {
            JComponent comboBoxEditor = (JComponent)component;
            comboBoxEditor.setBorder(ourDefaultEditorBorder);
            comboBoxEditor.addPropertyChangeListener("border", myEditorBorderChangeListener);
          }
        }
      }
    };
    c.addPropertyChangeListener("editor", myEditorChangeListener);
  }

  @Override
  public void uninstallUI(JComponent c) {
    c.removePropertyChangeListener("editor", myEditorChangeListener);
    ComboBoxEditor editor = ((JComboBox)c).getEditor();
    if (editor != null) {
      Component component = editor.getEditorComponent();
      if (component instanceof JComponent) {
        component.removePropertyChangeListener("border", myEditorBorderChangeListener);
      }
    }
    super.uninstallUI(c);
  }

  @Override
  protected JButton createArrowButton() {
    final Color bg = myComboBox.getBackground();
    final Color fg = myComboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {
      @Override
      public void paint(Graphics g2) {
        Icon icon = MacIntelliJIconCache.getIcon("comboRight", false, myComboBox.hasFocus(), myComboBox.isEnabled());
        icon.paintIcon(this, g2, 0, 0);
      }

      @Override
      public Dimension getPreferredSize() {
        return JBUI.size(DEFAULT_ICON.getIconWidth(), DEFAULT_ICON.getIconHeight());
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
    return new Dimension(Math.max(d.width + 7, DEFAULT_ICON.getIconWidth()), Math.max(d.height, DEFAULT_ICON.getIconHeight()));
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
              setBorder(ourDefaultEditorBorder);
            }

            @Override
            public Color getBackground() {
              if (!isEnabled()) {
                return Gray.xF8;
              }
              return super.getBackground();
            }

            public void setText(String s) {
              if (getText().equals(s)) {
                return;
              }
              super.setText(s);
            }

            @Override
            public void setBorder(Border border) {
            }

            @Override
            public Border getBorder() {
              return ourDefaultEditorBorder;
            }

            @Override
            public Dimension getPreferredSize() {
              Dimension size = super.getPreferredSize();
              return new Dimension(size.width, DEFAULT_ICON.getIconHeight() - 6);
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
    rect.height=Math.min(rect.height, DEFAULT_ICON.getIconHeight()-8);
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
        int buttonWidth = DEFAULT_ICON.getIconWidth();
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
  protected ComboPopup createPopup() {
    return new BasicComboPopup(myComboBox) {
      @Override
      protected void configurePopup() {
        super.configurePopup();
        setBorderPainted(false);
        setBorder(JBUI.Borders.empty());
        setBackground(Gray.xFF);
      }

      @Override
      protected void configureList() {
        super.configureList();
        wrapRenderer();
      }

      @Override
      protected PropertyChangeListener createPropertyChangeListener() {
        final PropertyChangeListener listener = super.createPropertyChangeListener();
        return new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            listener.propertyChange(evt);
            if ("renderer".equals(evt.getPropertyName())) {
              wrapRenderer();
            }
          }
        };
      }

      class ComboBoxRendererWrapper implements ListCellRenderer {
        private final ListCellRenderer myRenderer;

        public ComboBoxRendererWrapper(@NotNull ListCellRenderer renderer) {
          myRenderer = renderer;
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          Component c = myRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          BorderLayoutPanel panel = JBUI.Panels.simplePanel(c).withBorder(JBUI.Borders.empty(0, 8));
          panel.setBackground(c.getBackground());
          return panel;
        }
      }

      private void wrapRenderer() {
        ListCellRenderer renderer = list.getCellRenderer();
        if (!(renderer instanceof ComboBoxRendererWrapper) && renderer != null) {
          list.setCellRenderer(new ComboBoxRendererWrapper(renderer));
        }
      }
    };
  }

  @Override
  public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
    g.setColor(myComboBox.isEnabled() ? Gray.xFF : Gray.xF8);
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
    Graphics gg = g.create(clip.left, r.y, stop - clip.left, DEFAULT_ICON.getIconHeight());
    boolean enabled = c.isEnabled();
    boolean hasFocus = c.hasFocus();
    Icon icon = MacIntelliJIconCache.getIcon("comboLeft", false, hasFocus, enabled);
    icon.paintIcon(c,gg,0,0);
    int x = icon.getIconWidth();
    icon = MacIntelliJIconCache.getIcon("comboMiddle", false, hasFocus, enabled);
    while (x < stop) {
      icon.paintIcon(c, gg, x, 0);
      x+=icon.getIconWidth();
    }
    gg.dispose();
    icon = MacIntelliJIconCache.getIcon("comboRight", false, hasFocus, enabled);
    icon.paintIcon(c, g, r.x, r.y);

    if ( !comboBox.isEditable() ) {
      paintCurrentValue(g, rectangleForCurrentValue(), false);
    }
  }
}
