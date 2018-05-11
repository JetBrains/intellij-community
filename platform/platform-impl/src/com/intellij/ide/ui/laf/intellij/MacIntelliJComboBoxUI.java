// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.ui.Gray;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LafIconLookup;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.MINIMUM_WIDTH;
import static com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.ARC;
import static com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.BW;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJComboBoxUI extends DarculaComboBoxUI {
  private static final Icon ICON = EmptyIcon.create(LafIconLookup.getIcon("comboRight", false, false, true, false));
  private static final Icon EDITABLE_ICON = EmptyIcon.create(LafIconLookup.getIcon("comboRight", false, false, true, true));

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJComboBoxUI();
  }

  protected void installDarculaDefaults() {
    comboBox.setOpaque(false);
  }

  protected void uninstallDarculaDefaults() {}

    @Override
  protected JButton createArrowButton() {
    Color bg = comboBox.getBackground();
    Color fg = comboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {
      @Override
      public void paint(Graphics g) {
        if (!UIUtil.isUnderDefaultMacTheme()) return; // Paint events may still arrive after UI switch until entire UI is updated.

        Icon icon = LafIconLookup.getIcon("comboRight", false, false, comboBox.isEnabled(), comboBox.isEditable());
        if (getWidth() > icon.getIconWidth() || getHeight() > icon.getIconHeight()) {
          Image image = IconUtil.toImage(icon);
          UIUtil.drawImage(g, image, new Rectangle(0, 0, getWidth(), getHeight()), null);
        } else {
          icon.paintIcon(this, g, 0, 0);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        Icon icon = comboBox.isEditable() ? EDITABLE_ICON : ICON;
        return new Dimension(icon.getIconWidth(), icon.getIconHeight());
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  @Override
  protected Dimension getSizeWithButton(Dimension size, Dimension editorSize) {
    Insets i = comboBox.getInsets();
    Icon icon = comboBox.isEditable() ? EDITABLE_ICON : ICON;
    int iconWidth = icon.getIconWidth() + i.right;
    int iconHeight = icon.getIconHeight() + i.top + i.bottom;

    int editorHeight = editorSize != null ? editorSize.height + i.top + i.bottom + padding.top + padding.bottom: 0;
    int editorWidth = editorSize != null ? editorSize.width + i.left + padding.left + padding.right : 0;
    editorWidth = Math.max(editorWidth, MINIMUM_WIDTH.get() + i.left);

    return new Dimension(Math.max(size.width + padding.left, editorWidth + iconWidth),
                         Math.max(Math.max(iconHeight, size.height), editorHeight));
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
    return new CustomComboPopup(comboBox) {
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
    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(c.getSize());

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      Color background = comboBox.isEditable() ? comboBox.getEditor().getEditorComponent().getBackground() :
                           UIManager.getColor(comboBox.isEnabled() ? "ComboBox.background" : "ComboBox.disabledBackground");
      g2.setColor(background);

      float arc = comboBox.isEditable() ? 0 : ARC.getFloat();
      float bw = BW.getFloat();

      Area bgs = new Area(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
      bgs.subtract(new Area(arrowButton.getBounds()));

      g2.fill(bgs);
    } finally {
      g2.dispose();
    }


    if ( !comboBox.isEditable() ) {
      listBox.setForeground(comboBox.isEnabled() ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));
      checkFocus();
      paintCurrentValue(g, rectangleForCurrentValue(), hasFocus);
    }
  }

  @Nullable Rectangle getArrowButtonBounds() {
    return arrowButton != null ? arrowButton.getBounds() : null;
  }
}
