/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
public abstract class ColoredListCellRenderer<T> extends SimpleColoredComponent implements ListCellRenderer<T> {
  private final ListCellRenderer myDefaultGtkRenderer = UIUtil.isUnderGTKLookAndFeel() ? new JComboBox<T>().getRenderer() : null;

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

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
    clear();

    if (myComboBox != null) {
      setEnabled(myComboBox.isEnabled());
    }
    setFont(list.getFont());
    mySelected = selected;
    myForeground = isEnabled() ? list.getForeground() : UIManager.getColor("Label.disabledForeground");
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

    customizeCellRenderer(list, value, index, selected, hasFocus);

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
  @Override
  public final void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText) {
    if (mySelected) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), mySelectionForeground), isMainText);
    }
    else if (attributes.getFgColor() == null) {
      super.append(fragment, attributes.derive(-1, myForeground, null, null), isMainText);
    }
    else {
      super.append(fragment, attributes, isMainText);
    }
  }

  @Override
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

  protected abstract void customizeCellRenderer(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus);

  /**
   * Copied AS IS
   *
   * @see javax.swing.DefaultListCellRenderer#isOpaque()
   */
  @Override
  public boolean isOpaque() {
    Color back = getBackground();
    Component p = getParent();
    if (p != null) {
      p = p.getParent();
    }
    // p should now be the JList.
    boolean colorMatch = (back != null) && (p != null) &&
                         back.equals(p.getBackground()) &&
                         p.isOpaque();
    return !colorMatch && super.isOpaque();
  }
}
