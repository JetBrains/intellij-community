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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class ColoredListCellRenderer<T> extends SimpleColoredComponent implements ListCellRenderer {
  private final ListCellRenderer myDefaultGtkRenderer = UIUtil.isUnderGTKLookAndFeel() ? new JComboBox().getRenderer() : null;

  protected boolean mySelected;
  protected Color myForeground;
  protected Color mySelectionForeground;
  @Nullable
  private final JComboBox myComboBox;

  public ColoredListCellRenderer() {
    this(null);
  }

  public ColoredListCellRenderer(@Nullable JComboBox comboBox) {
    myComboBox = comboBox;
    setFocusBorderAroundIcon(true);
    getIpad().left = UIUtil.getListCellHPadding();
    getIpad().right = UIUtil.getListCellHPadding();
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    clear();

    if (myComboBox != null) {
      setEnabled(myComboBox.isEnabled());
    }
    setFont(list.getFont());
    mySelected = selected;
    myForeground = list.getForeground();
    mySelectionForeground = list.getSelectionForeground();
    if (UIUtil.isWinLafOnVista()) {
      // the system draws a gradient background on the combobox selected item - don't overdraw it with our solid background
      if (index == -1) {
        setOpaque(false);
        mySelected = false;
      }
      else {
        setOpaque(true);
        setBackground(selected ? list.getSelectionBackground() : null);
      }
    }
    else {
      setBackground(selected ? list.getSelectionBackground() : null);
    }

    setPaintFocusBorder(hasFocus);

    customizeCellRenderer(list, (T)value, index, selected, hasFocus);

    if (myDefaultGtkRenderer != null && list.getModel() instanceof ComboBoxModel) {
      final Component component = myDefaultGtkRenderer.getListCellRendererComponent(list, value, index, selected, hasFocus);
      if (component instanceof JLabel) {
        return formatToLabel((JLabel)component);
      }
    }
    return this;
  }

  /**
   * When the item is selected then we use default tree's selection foreground.
   * It guaranties readability of selected text in any LAF.
   */
  public final void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText) {
    if (mySelected) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), mySelectionForeground), isMainText);
    }
    else if (attributes.getFgColor() == null) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), myForeground), isMainText);
    }
    else {
      super.append(fragment, attributes, isMainText);
    }
  }

  @NotNull
  public Dimension getPreferredSize() {
    // There is a bug in BasicComboPopup. It does not add renderer into CellRendererPane,
    // so font can be null here.

    Font oldFont = getFont();
    if (oldFont == null) {
      setFont(UIUtil.getListFont());
    }
    Dimension result = super.getPreferredSize();
    if (oldFont == null) {
      setFont(null);
    }

    return result;
  }

  protected abstract void customizeCellRenderer(JList list, T value, int index, boolean selected, boolean hasFocus);

  public abstract static class KotlinFriendlyColoredListCellRenderer<T> extends ColoredListCellRenderer<T> {
    @Override
    protected final void customizeCellRenderer(JList list, T value, int index, boolean selected, boolean hasFocus) {

    }

    // cannot specify type param in JList if JDK 6
    protected abstract void customizeCellRenderer(T value, int index, boolean selected, boolean hasFocus);
  }
}
