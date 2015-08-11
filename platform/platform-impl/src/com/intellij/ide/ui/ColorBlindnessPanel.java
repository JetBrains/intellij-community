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
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * @author Sergey.Malenkov
 */
final class ColorBlindnessPanel extends JPanel implements ChangeListener {
  private final JCheckBox myCheckBox = new JCheckBox();
  private final JComboBox myComboBox = new ComboBox();

  public ColorBlindnessPanel() {
    super(new HorizontalLayout(JBUI.scale(10)));
    add(HorizontalLayout.LEFT, myCheckBox);
    add(HorizontalLayout.LEFT, myComboBox);
    myCheckBox.setSelected(false);
    myCheckBox.addChangeListener(this);
    myCheckBox.setText(IdeBundle.message("checkbox.color.blindness"));
    int count = 0;
    for (ColorBlindness blindness : ColorBlindness.values()) {
      String name = IdeBundle.message("combobox.color.blindness." + blindness.name());
      if (!name.isEmpty()) {
        myComboBox.addItem(new Item(blindness, name));
        count++;
      }
    }
    myComboBox.setEnabled(false);
    myComboBox.setVisible(count > 1);
    setVisible(count > 0);
  }

  @Override
  public void stateChanged(ChangeEvent event) {
    myComboBox.setEnabled(myCheckBox.isSelected());
  }

  public ColorBlindness getColorBlindness() {
    if (myCheckBox.isSelected()) {
      Object object = myComboBox.getSelectedItem();
      if (object instanceof Item) {
        Item item = (Item)object;
        return item.myBlindness;
      }
    }
    return null;
  }

  public void setColorBlindness(ColorBlindness blindness) {
    Item item = null;
    if (blindness != null) {
      int count = myComboBox.getItemCount();
      for (int i = 0; i < count && item == null; i++) {
        Object object = myComboBox.getItemAt(i);
        if (object instanceof Item) {
          item = (Item)object;
          if (item.myBlindness != blindness) {
            item = null;
          }
        }
      }
    }
    myCheckBox.setSelected(item != null);
    if (item != null) {
      myComboBox.setSelectedItem(item);
    }
  }

  private static final class Item {
    private final ColorBlindness myBlindness;
    private final String myName;

    private Item(ColorBlindness blindness, String name) {
      myBlindness = blindness;
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }
  }
}
