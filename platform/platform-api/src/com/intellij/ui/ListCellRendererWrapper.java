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
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

import static com.intellij.openapi.util.Pair.pair;

/**
 * Please use this wrapper in case you need simple cell renderer with text and icon.
 * This avoids ugly UI under GTK+ look&feel, because in this case SynthComboBoxUI#SynthComboBoxRenderer
 * is used instead of DefaultComboBoxRenderer.
 *
 * @author oleg
 * @since 30.09.2010
 */
public abstract class ListCellRendererWrapper<T> implements ListCellRenderer {
  private final ListCellRenderer myDefaultRenderer;

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
    ListCellRenderer renderer = new JComboBox().getRenderer();
    if (renderer == null) {
      renderer = new BasicComboBoxRenderer();
      Logger.getInstance(this.getClass()).error("LaF: " + UIManager.getLookAndFeel());
    }
    myDefaultRenderer = renderer;
  }

  public final Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    mySeparator = false;
    myIcon = null;
    myText = null;
    myForeground = null;
    myBackground = null;
    myFont = null;
    myToolTipText = null;
    myProperties = FList.emptyList();

    @SuppressWarnings("unchecked") final T t = (T)value;
    customize(list, t, index, isSelected, cellHasFocus);

    if (mySeparator) {
      final TitledSeparator separator = new TitledSeparator(myText);
      separator.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        separator.setOpaque(false);
        separator.setBackground(UIUtil.TRANSPARENT_COLOR);
        separator.getLabel().setOpaque(false);
        separator.getLabel().setBackground(UIUtil.TRANSPARENT_COLOR);
      }
      return separator;
    }

    @SuppressWarnings("unchecked") final Component component = myDefaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (component instanceof JLabel) {
      final JLabel label = (JLabel)component;
      label.setIcon(myIcon);
      if (myText != null) label.setText(myText);
      if (myForeground != null) label.setForeground(myForeground);
      if (myBackground != null && !isSelected) label.setBackground(myBackground);
      if (myFont != null) label.setFont(myFont);
      label.setToolTipText(myToolTipText);
      for (Pair<Object, Object> pair : myProperties) {
        label.putClientProperty(pair.first, pair.second);
      }
    }
    return component;
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
