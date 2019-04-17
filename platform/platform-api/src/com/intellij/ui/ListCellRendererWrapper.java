// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.util.Pair.pair;

/**
 * Use this class when you need a customizable text-and-icon cell renderer.
 *
 * @author oleg
 */
public abstract class ListCellRendererWrapper<T> implements ListCellRenderer<T> {
  private final ListCellRenderer<? super T> myRenderer;
  private boolean mySeparator;
  private Icon myIcon;
  private String myText;
  private String myToolTipText;
  private Color myForeground;
  private Color myBackground;
  private Font myFont;
  private FList<Pair<Object, Object>> myProperties = FList.emptyList();

  @SuppressWarnings("UndesirableClassUsage")
  public ListCellRendererWrapper() {
    myRenderer = new JComboBox<T>().getRenderer();
  }

  @Override
  public final Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
    mySeparator = false;
    myIcon = null;
    myText = null;
    myForeground = null;
    myBackground = null;
    myFont = null;
    myToolTipText = null;
    myProperties = FList.emptyList();

    customize(list, value, index, selected, hasFocus);

    if (mySeparator) {
      return createSeparator(myText);
    }

    Component component = myRenderer != null ? myRenderer.getListCellRendererComponent(list, value, index, selected, hasFocus) : new JBLabel();
    if (component instanceof JLabel) {
      JLabel label = (JLabel)component;
      label.setIcon(myIcon);
      if (myText != null) label.setText(myText);
      if (myForeground != null) label.setForeground(myForeground);
      if (myBackground != null && !selected) label.setBackground(myBackground);
      if (myFont != null) label.setFont(myFont);
      label.setToolTipText(myToolTipText);
      for (Pair<Object, Object> pair : myProperties) {
        label.putClientProperty(pair.first, pair.second);
      }
    }
    return component;
  }

  @NotNull
  public static Component createSeparator(@Nullable String text) {
    TitledSeparator separator = new TitledSeparator(text);
    separator.setBorder(JBUI.Borders.emptyLeft(2));
    separator.setOpaque(false);
    separator.setBackground(UIUtil.TRANSPARENT_COLOR);
    separator.getLabel().setOpaque(false);
    separator.getLabel().setBackground(UIUtil.TRANSPARENT_COLOR);
    return separator;
  }

  /**
   * Implement this method to configure text and icon for given value.
   * Use {@link #setIcon(Icon)} and {@link #setText(String)} methods.
   *
   * @param list     The JList we're painting.
   * @param value    The value returned by list.getModel().getElementAt(index).
   * @param index    The cells index.
   * @param selected True if the specified cell was selected.
   * @param hasFocus True if the specified cell has the focus.
   */
  public abstract void customize(final JList list, final T value, final int index, final boolean selected, final boolean hasFocus);

  public final void setSeparator() {
    mySeparator = true;
  }

  public final void setIcon(@Nullable final Icon icon) {
    myIcon = icon;
  }

  public final void setText(@Nullable final String text) {
    myText = text;
  }

  public final void setToolTipText(@Nullable final String toolTipText) {
    myToolTipText = toolTipText;
  }

  public final void setForeground(@Nullable final Color foreground) {
    myForeground = foreground;
  }

  public final void setBackground(@Nullable final Color background) {
    myBackground = background;
  }

  public final void setFont(@Nullable final Font font) {
    myFont = font;
  }

  public final void setClientProperty(@NotNull final Object key, @Nullable final Object value) {
    myProperties = myProperties.prepend(pair(key, value));
  }
}
