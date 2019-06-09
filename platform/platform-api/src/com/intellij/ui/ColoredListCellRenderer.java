// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.list.ListCellBackgroundSupplier;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * SimpleColoredComponent-based list cell renderer.
 *
 * @see SimpleListCellRenderer for a simpler JBLabel-based variant.
 * @see ListCellBackgroundSupplier for different background color.
 *
 * @author Vladimir Kondratyev
 */
public abstract class ColoredListCellRenderer<T> extends SimpleColoredComponent implements ListCellRenderer<T> {

  protected boolean mySelected;
  protected Color myForeground;
  protected Color mySelectionForeground;

  public ColoredListCellRenderer() {
    setFocusBorderAroundIcon(true);
    getIpad().left = getIpad().right = UIUtil.isUnderWin10LookAndFeel() ? 0 : JBUIScale.scale(UIUtil.getListCellHPadding());
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
    clear();
    setFont(list.getFont());
    mySelected = selected;
    myForeground = isEnabled() ? list.getForeground() : UIUtil.getLabelDisabledForeground();
    mySelectionForeground = list.getSelectionForeground();

    if (UIUtil.isUnderWin10LookAndFeel()) {
      setBackground(selected ? list.getSelectionBackground() : list.getBackground());
    }
    else {
      setBackground(selected ? list.getSelectionBackground() : null);
    }

    setPaintFocusBorder(hasFocus);
    customizeCellRenderer(list, value, index, selected, hasFocus);

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

    return UIUtil.updateListRowHeight(result);
  }

  protected abstract void customizeCellRenderer(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus);

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
  // @formatter:on
}