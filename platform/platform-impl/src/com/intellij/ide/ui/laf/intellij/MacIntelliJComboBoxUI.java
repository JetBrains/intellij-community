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

import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.ui.Gray;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
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
public class MacIntelliJComboBoxUI extends DarculaComboBoxUI {
  private static final Border ourDefaultEditorBorder = JBUI.Borders.empty(1, 0);

  private Icon DEFAULT_ICON;
  private PropertyChangeListener myEditorChangeListener;
  private PropertyChangeListener myEditorBorderChangeListener;
  private PropertyChangeListener myEditableChangeListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJComboBoxUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);

    DEFAULT_ICON = EmptyIcon.create(MacIntelliJIconCache.getIcon("comboRight", comboBox.isEditable(), false, false, true));
    comboBox.setOpaque(false);
    comboBox.setBorder(new MacComboBoxBorder());

    myEditorBorderChangeListener = (evt) -> {
      Object value = evt.getNewValue();
      if (value == ourDefaultEditorBorder) return;
      ComboBoxEditor editor = ((JComboBox)c).getEditor();
      if (editor != null) {
        Component component = editor.getEditorComponent();
        if (component instanceof JComponent) {
          ((JComponent)component).setBorder(ourDefaultEditorBorder);
        }
      }
    };

    myEditorChangeListener = (evt) -> {
      Object oldValue = evt.getOldValue();
      if (oldValue instanceof ComboBoxEditor) {
        Component component = ((ComboBoxEditor)oldValue).getEditorComponent();
        if (component instanceof JComponent) {
          component.removePropertyChangeListener("border", myEditorBorderChangeListener);
        }
      }

      Object newValue = evt.getNewValue();
      if (newValue instanceof ComboBoxEditor) {
        Component component = ((ComboBoxEditor)evt.getNewValue()).getEditorComponent();
        if (component instanceof JComponent) {
          JComponent comboBoxEditor = (JComponent)component;
          comboBoxEditor.setBorder(ourDefaultEditorBorder);
          comboBoxEditor.setOpaque(false);
          comboBoxEditor.addPropertyChangeListener("border", myEditorBorderChangeListener);
        }
      }
    };

    c.addPropertyChangeListener("editor", myEditorChangeListener);

    myEditableChangeListener = (evt) -> {
      Boolean editable = (Boolean)evt.getNewValue();
      DEFAULT_ICON = EmptyIcon.create(MacIntelliJIconCache.getIcon("comboRight", editable, false, false, false));
      comboBox.invalidate();
    };
    c.addPropertyChangeListener("editable", myEditableChangeListener);
  }

  @Override
  public void uninstallUI(JComponent c) {
    c.removePropertyChangeListener("editor", myEditorChangeListener);
    c.removePropertyChangeListener("editable", myEditableChangeListener);
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
    Color bg = comboBox.getBackground();
    Color fg = comboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {
      @Override
      public void paint(Graphics g) {
        Icon icon = MacIntelliJIconCache.getIcon("comboRight", comboBox.isEditable(), false, false, comboBox.isEnabled());
        if (getWidth() > icon.getIconWidth() || getHeight() > icon.getIconHeight()) {
          Image image = IconUtil.toImage(icon);
          UIUtil.drawImage(g, image, new Rectangle(0, 0, getWidth(), getHeight()), null, null);
        } else {
          icon.paintIcon(this, g, 0, 0);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(DEFAULT_ICON.getIconWidth(), DEFAULT_ICON.getIconHeight());
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  protected Dimension getSizeWithButton(Dimension d) {
    Insets i = comboBox.getInsets();
    int iconWidth = DEFAULT_ICON.getIconWidth() + i.right;
    int iconHeight = DEFAULT_ICON.getIconHeight() + i.top + i.bottom;
    int editorHeight = editor != null ? editor.getPreferredSize().height + i.top + i.bottom : 0;
    return new Dimension(Math.max(d.width + JBUI.scale(7), iconWidth),
                         Math.max(Math.max(iconHeight, editorHeight), JBUI.scale(26)));
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
              return (comboBox != null && !comboBox.isEnabled()) ?
                     UIManager.getColor("ComboBox.disabledBackground") :
                     super.getBackground();
            }

            public void setText(String s) {
              if (getText().equals(s)) {
                return;
              }
              super.setText(s);
            }

            @Override
            public Border getBorder() {
              return ourDefaultEditorBorder;
            }

            @Override
            public Insets getInsets() {
              return ourDefaultEditorBorder.getBorderInsets(this);
            }

            @Override
            public Dimension getPreferredSize() {
              Dimension size = super.getPreferredSize();
              return new Dimension(size.width, DEFAULT_ICON.getIconHeight());
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
  protected LayoutManager createLayoutManager() {
    return new ComboBoxLayoutManager() {
      @Override
      public void layoutContainer(Container parent) {
        JComboBox cb = (JComboBox)parent;

        Dimension size = cb.getMinimumSize();
        Rectangle bounds = cb.getBounds();
        bounds.height = bounds.height < size.height ? size.height : bounds.height;

        size = cb.getPreferredSize();
        bounds.height = bounds.height > size.height ? size.height : bounds.height;
        cb.setBounds(bounds);

        Insets cbInsets = cb.getInsets();
        if (arrowButton != null) {
          Dimension prefSize = arrowButton.getPreferredSize();

          int buttonHeight = bounds.height - (cbInsets.top + cbInsets.bottom);
          double ar = (double)buttonHeight / prefSize.height;
          int buttonWidth = (int)Math.floor(prefSize.width * ar);
          int offset = (int)Math.round(ar - 1.0);

          arrowButton.setBounds(bounds.width - buttonWidth - cbInsets.right + offset, cbInsets.top, buttonWidth, buttonHeight);
        }

        layoutEditor();
      }
    };
  }

  @Override
  protected ComboPopup createPopup() {
    return new BasicComboPopup(comboBox) {
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

      @SuppressWarnings("unchecked")
      private void wrapRenderer() {
        ListCellRenderer<Object> renderer = list.getCellRenderer();
        if (!(renderer instanceof ComboBoxRendererWrapper) && renderer != null) {
          list.setCellRenderer(new ComboBoxRendererWrapper(renderer));
        }
      }
    };
  }

  private static class ComboBoxRendererWrapper implements ListCellRenderer<Object> {
    private final ListCellRenderer<Object> myRenderer;

    public ComboBoxRendererWrapper(@NotNull ListCellRenderer<Object> renderer) {
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

  @Override
  public void paint(Graphics g, JComponent c) {
    Rectangle bounds = rectangleForCurrentValue();

    if ( !comboBox.isEditable() ) {
      listBox.setForeground(comboBox.isEnabled() ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));
      paintCurrentValue(g, bounds, comboBox.isPopupVisible());
    }
  }

  @Nullable Rectangle getArrowButtonBounds() {
    return arrowButton != null ? arrowButton.getBounds() : null;
  }
}
