// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ComboBoxWithWidePopup<E> extends JComboBox<E> {
  private int myMinLength = 20;

  public ComboBoxWithWidePopup() {
    init();
  }

  public ComboBoxWithWidePopup(@NotNull ComboBoxModel<E> model) {
    super(model);
    init();
  }

  public ComboBoxWithWidePopup(@NotNull E[] items) {
    super(items);
    init();
  }

  private void init() {
    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) {
      setMaximumRowCount(25);
    }
  }

  @Override
  public void setRenderer(ListCellRenderer<? super E> renderer) {
    if (renderer instanceof SimpleColoredComponent) {
      SimpleColoredComponent scc = (SimpleColoredComponent)renderer;
      scc.getIpad().top = scc.getIpad().bottom = 0;
    }

    super.setRenderer(new AdjustingListCellRenderer(renderer));
  }

  public void setMinLength(int minLength) {
    myMinLength = minLength;
  }

  /**
   * @return min string len to show
   */
  protected int getMinLength() {
    return myMinLength;
  }

  /**
   * @return minimum width of a popup that is wide enough to show all the combobox's items horizontally
   */
  public int getMinimumPopupWidth() {
    // Original preferred size of JComboBox is calculated as max of all pref sizes of combobox's items.
    // See javax.swing.plaf.basic.BasicComboBoxUI#getDisplaySize()
    //
    // Please note that "getPreferredSize().width" cannot be used as getPreferredSize might be overridden
    // to return a value different to "max of all pref sizes of comboBox's items".
    return super.getPreferredSize().width;
  }

  private class AdjustingListCellRenderer implements ListCellRenderer<E> {
    private final ListCellRenderer<? super E> delegate;

    AdjustingListCellRenderer(ListCellRenderer<? super E> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
      if (index == -1 && value instanceof String && !isValid()) {
        int minLength = getMinLength();
        String stringValue = (String)value;

        if (getSize().width == 0) {
          if (stringValue.length() > minLength) {
            @SuppressWarnings("unchecked") E e = (E)stringValue.substring(0, minLength);
            value = e;
          }
        }
      }

      return delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
  }
}