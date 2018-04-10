// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class ColoredListCellRenderer<T> extends SimpleColoredComponent implements ListCellRenderer<T> {
  private final @Nullable JComboBox myComboBox;
  private final @Nullable ListCellRenderer<? super T> myDefaultGtkRenderer;

  protected boolean mySelected;
  protected Color myForeground;
  protected Color mySelectionForeground;

  public ColoredListCellRenderer() {
    this(null);
  }

  public ColoredListCellRenderer(@Nullable JComboBox comboBox) {
    myComboBox = comboBox;
    //noinspection UndesirableClassUsage
    myDefaultGtkRenderer = UIUtil.isUnderGTKLookAndFeel() ? new JComboBox<T>().getRenderer() : null;
    setFocusBorderAroundIcon(true);
    getIpad().left = getIpad().right = UIUtil.isUnderWin10LookAndFeel() ? 0 : JBUI.scale(UIUtil.getListCellHPadding());
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
    else if (UIUtil.isUnderWin10LookAndFeel()) {
      setBackground(selected ? list.getSelectionBackground() : list.getBackground());
    }
    else {
      setBackground(selected ? list.getSelectionBackground() : null);
    }

    setPaintFocusBorder(hasFocus);

    customizeCellRenderer(list, value, index, selected, hasFocus);

    if (myDefaultGtkRenderer != null && list.getModel() instanceof ComboBoxModel) {
      Component component = myDefaultGtkRenderer.getListCellRendererComponent(list, value, index, selected, hasFocus);
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
  void revalidateAndRepaint() {
    // no need for this in a renderer
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
   * @see DefaultListCellRenderer#isOpaque()
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