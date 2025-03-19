// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import javax.swing.*;
import java.awt.*;

public class ComboBoxWithWidePopup<E> extends JComboBox<E> {
  private int myMinLength = 20;

  public ComboBoxWithWidePopup() { }

  public ComboBoxWithWidePopup(@NotNull ComboBoxModel<E> model) {
    super(model);
  }

  public ComboBoxWithWidePopup(E @NotNull [] items) {
    super(items);
  }

  @SuppressWarnings("unchecked")
  public @UnknownNullability E getItem() {
    return (E)getSelectedItem();
  }

  public void setItem(E item) {
    setSelectedItem(item);
  }

  @Override
  public void setRenderer(ListCellRenderer<? super E> renderer) {
    if (renderer instanceof SimpleColoredComponent scc) {
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
   * @return minimum width of a popup that is wide enough to show all the combobox items horizontally
   */
  @SuppressWarnings({"SpellCheckingInspection", "StructuralWrap"})
  public int getMinimumPopupWidth() {
    // The original preferred size of `JComboBox` is calculated as a max of combobox items preferred sizes
    // (see `javax.swing.plaf.basic.BasicComboBoxUI#getDisplaySize`).
    // Please note that `getPreferredSize().width` cannot be used, because `getPreferredSize` might be overridden
    // to return a value different from "a max of combobox items preferred sizes".
    return super.getPreferredSize().width;
  }

  public class AdjustingListCellRenderer implements ListCellRenderer<E> {

    @ApiStatus.Internal
    public final ListCellRenderer<? super E> delegate;

    AdjustingListCellRenderer(ListCellRenderer<? super E> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
      if (index == -1 && value instanceof String stringValue && !isValid()) {
        int minLength = getMinLength();

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
