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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final Border ourDefaultEditorBorder = JBUI.Borders.empty(1, 0);

  static final int VALUE_OFFSET = JBUI.scale(5);
  private Icon DEFAULT_ICON;

  private PropertyChangeListener myEditorChangeListener;
  private PropertyChangeListener myEditorBorderChangeListener;
  private PropertyChangeListener myEditableChangeListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJComboBoxUI();
  }

  @Override
  public void installUI(final JComponent c) {
    super.installUI(c);

    DEFAULT_ICON = EmptyIcon.create(MacIntelliJIconCache.getIcon("comboRight", comboBox.isEditable(), false, false, true));
    comboBox.setOpaque(false);

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
        icon.paintIcon(this, g, 0, 0);
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

  private Dimension getSizeWithIcon(Dimension d) {
    Insets i = comboBox.getInsets();
    int iconWidth = DEFAULT_ICON.getIconWidth() + i.right;
    int iconHeight = DEFAULT_ICON.getIconHeight() + i.top + i.bottom;
    return new Dimension(Math.max(d.width + 7, iconWidth), iconHeight);
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
            public void setBorder(Border border) {
            }

            @Override
            public Border getBorder() {
              return ourDefaultEditorBorder;
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
  protected Rectangle rectangleForCurrentValue() {
    Rectangle rect = super.rectangleForCurrentValue();
    rect.x += VALUE_OFFSET;
    rect.width -= VALUE_OFFSET;
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
      public void addLayoutComponent(String name, Component comp) {}

      @Override
      public void removeLayoutComponent(Component comp) {}

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

        Dimension size = cb.getMinimumSize();
        Rectangle bounds = cb.getBounds();
        bounds.height = bounds.height < size.height ? size.height : bounds.height;

        size = cb.getPreferredSize();
        bounds.height = bounds.height > size.height ? size.height : bounds.height;
        cb.setBounds(bounds);

        Insets cbInsets = cb.getInsets();
        if (arrowButton != null) {
          Insets arrowInsets = arrowButton.getInsets();
          Dimension prefSize = arrowButton.getPreferredSize();
          int buttonWidth = prefSize.width + arrowInsets.left + arrowInsets.right;
          int buttonHeight = prefSize.height + arrowInsets.top + arrowInsets.bottom;

          arrowButton.setBounds(bounds.width - buttonWidth - cbInsets.right, cbInsets.top, buttonWidth, buttonHeight);
        }

        if (editor != null ) {
          bounds = rectangleForCurrentValue();
          Insets editorInsets = ourDefaultEditorBorder.getBorderInsets(editor);
          bounds.y += editorInsets.top;
          bounds.height -= editorInsets.top + editorInsets.bottom;
          editor.setBounds(bounds);
        }
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

      class ComboBoxRendererWrapper implements ListCellRenderer<Object> {
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

      @SuppressWarnings("unchecked")
      private void wrapRenderer() {
        ListCellRenderer<Object> renderer = list.getCellRenderer();
        if (!(renderer instanceof ComboBoxRendererWrapper) && renderer != null) {
          list.setCellRenderer(new ComboBoxRendererWrapper(renderer));
        }
      }
    };
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

  @Override
  protected void configureEditor() {
    super.configureEditor();
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
}
