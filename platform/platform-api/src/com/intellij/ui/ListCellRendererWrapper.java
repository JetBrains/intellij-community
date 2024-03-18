// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
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
 * @deprecated use {@link SimpleListCellRenderer} instead.
 *
 * @author oleg
 */
@Deprecated
public abstract class ListCellRendererWrapper<T> implements ListCellRenderer<T> {
  private final ListCellRenderer<? super T> myRenderer;
  private boolean mySeparator;
  private Icon myIcon;
  private @NlsContexts.ListItem String myText;
  private @NlsContexts.Tooltip String myToolTipText;
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
    if (component instanceof JLabel label) {
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

  /**
   * @deprecated Use plain {@link JSeparator} instead
   */
  @Deprecated(forRemoval = true)
  private static @NotNull Component createSeparator(@Nullable @NlsContexts.Separator String text) {
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
  public abstract void customize(JList list, T value, int index, boolean selected, boolean hasFocus);

  public final void setSeparator() {
    mySeparator = true;
  }

  public final void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  public final void setText(@Nullable @NlsContexts.ListItem String text) {
    myText = text;
  }

  public final void setToolTipText(@Nullable @NlsContexts.Tooltip String toolTipText) {
    myToolTipText = toolTipText;
  }

  public final void setForeground(@Nullable Color foreground) {
    myForeground = foreground;
  }

  public final void setBackground(@Nullable Color background) {
    myBackground = background;
  }

  public final void setFont(@Nullable Font font) {
    myFont = font;
  }

  public final void setClientProperty(@NotNull Object key, @Nullable Object value) {
    myProperties = myProperties.prepend(pair(key, value));
  }
}