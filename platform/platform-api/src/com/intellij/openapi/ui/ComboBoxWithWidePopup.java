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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class ComboBoxWithWidePopup<E> extends JComboBox<E> {
  private boolean myLayingOut = false;
  private int myMinLength = 20;

  public ComboBoxWithWidePopup() { }

  public ComboBoxWithWidePopup(final ComboBoxModel<E> aModel) {
    super(aModel);
    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) setMaximumRowCount(25);
  }

  public ComboBoxWithWidePopup(final E[] items) {
    super(items);
    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) setMaximumRowCount(25);
  }

  @SuppressWarnings("GtkPreferredJComboBoxRenderer")
  @Override
  public void setRenderer(ListCellRenderer<? super E> renderer) {
    super.setRenderer(new AdjustingListCellRenderer(this, renderer));
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

  public void doLayout() {
    try {
      myLayingOut = true;
      super.doLayout();
    }
    finally {
      myLayingOut = false;
    }
  }

  public Dimension getSize() {
    Dimension size = super.getSize();
    if (!myLayingOut) {
      size.width = Math.max(size.width, getOriginalPreferredSize().width);
    }
    return size;
  }
  
  private Dimension _getSuperSize() {
    return super.getSize();
  }

  protected Dimension getOriginalPreferredSize() {
    return getPreferredSize();
  }

  private class AdjustingListCellRenderer implements ListCellRenderer<E> {
    private final ListCellRenderer<? super E> myOldRenderer;
    private final ComboBoxWithWidePopup myComboBox;

    public AdjustingListCellRenderer(ComboBoxWithWidePopup<E> comboBox, ListCellRenderer<? super E> oldRenderer) {
      myComboBox = comboBox;
      myOldRenderer = oldRenderer;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
      E _value = value;
      if (index == -1 && _value instanceof String && !myComboBox.isValid()) {
        int minLength = getMinLength();

        Dimension size = myComboBox._getSuperSize();
        String stringValue = (String)_value;

        if (size.width == 0) {
          if (stringValue.length() > minLength) {
            @SuppressWarnings("unchecked") E e = (E)stringValue.substring(0, minLength);
            _value = e;
          }
        }
      }

      return myOldRenderer.getListCellRendererComponent(list, _value, index, isSelected, cellHasFocus);
    }
  }
}