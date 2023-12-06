// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.DefaultLookup;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;

/**
 * JBLabel-based (text and icon) list cell renderer.
 *
 * @see ColoredListCellRenderer for more complex SimpleColoredComponent-based variant.
 *
 * @author gregsh
 */
public abstract class SimpleListCellRenderer<T> extends JBLabel implements ListCellRenderer<T> {
  public static @NotNull <T> SimpleListCellRenderer<@Nullable T> create(@NotNull @NlsContexts.Label String nullValue,
                                                                        @NotNull Function<? super T, @NlsContexts.Label String> getText) {
    return new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
        setText(value == null ? nullValue : getText.fun(value));
      }
    };
  }

  public static @NotNull <T> SimpleListCellRenderer<T> create(@NotNull Customizer<? super T> customizer) {
    return new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
        customizer.customize(this, value, index);
      }
    };
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean cellHasFocus) {
    setComponentOrientation(list.getComponentOrientation());
    setBorder(JBUI.Borders.empty(UIUtil.getListCellVPadding(), UIUtil.getListCellHPadding()));
    Color bg, fg;
    JList.DropLocation dropLocation = list.getDropLocation();
    if (dropLocation != null && !dropLocation.isInsert() && dropLocation.getIndex() == index) {
      bg = DefaultLookup.getColor(this, ui, "List.dropCellBackground");
      fg = DefaultLookup.getColor(this, ui, "List.dropCellForeground");
      isSelected = true;
    }
    else {
      bg = RenderingUtil.getBackground(list, isSelected);
      fg = RenderingUtil.getForeground(list, isSelected);
    }
    setBackground(bg);
    setForeground(fg);
    setFont(list.getFont());
    setText("");
    setIcon(null);
    customize(list, value, index, isSelected, cellHasFocus);
    setOpaque(isSelected);
    return this;
  }

  public abstract void customize(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus);

  @Override
  public Dimension getPreferredSize() {
    if (StringUtil.isNotEmpty(getText())) {
      return super.getPreferredSize();
    }
    setText(" ");
    Dimension size = super.getPreferredSize();
    setText("");
    return size;
  }

  @FunctionalInterface
  public interface Customizer<T> {
    void customize(@NotNull JBLabel label, T value, int index);
  }

  // @formatter:off
  @Override public void validate() {}
  @Override public void invalidate() {}
  @Override public void repaint() {}
  @Override public void revalidate() {}
  @Override public void repaint(long tm, int x, int y, int width, int height) {}
  @Override public void repaint(Rectangle r) {}
  @Override public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {}
  @Override public void firePropertyChange(String propertyName, char oldValue, char newValue) {}
  @Override public void firePropertyChange(String propertyName, short oldValue, short newValue) {}
  @Override public void firePropertyChange(String propertyName, int oldValue, int newValue) {}
  @Override public void firePropertyChange(String propertyName, long oldValue, long newValue) {}
  @Override public void firePropertyChange(String propertyName, float oldValue, float newValue) {}
  @Override public void firePropertyChange(String propertyName, double oldValue, double newValue) {}
  @Override public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    if ("text".equals(propertyName)
        || (("font".equals(propertyName) || "foreground".equals(propertyName))
            && oldValue != newValue
            && getClientProperty(BasicHTML.propertyKey) != null)) {
      super.firePropertyChange(propertyName, oldValue, newValue);
    }
  }
  // @formatter:on
}
