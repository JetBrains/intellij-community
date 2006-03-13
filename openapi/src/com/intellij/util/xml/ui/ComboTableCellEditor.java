/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class ComboTableCellEditor extends DefaultCellEditor {
  private final boolean myNullable;
  private final Factory<List<String>> myDataFactory;
  private Set<String> myData;
  private static final String EMPTY = " ";

  public ComboTableCellEditor(Factory<List<String>> dataFactory, final boolean nullable) {
    super(new JComboBox());
    myDataFactory = dataFactory;
    myNullable = nullable;
    setClickCountToStart(2);
    JComboBox comboBox = (JComboBox)editorComponent;
    ComboControl.initComboBox(comboBox, new Condition<String>() {
      public boolean value(final String object) {
        return myData != null && (myData.contains(object) || myNullable && EMPTY.equals(object));
      }
    });
  }

  public ComboTableCellEditor(Class<? extends Enum> anEnum, final boolean nullable) {
    this(ComboControl.createEnumFactory(anEnum), nullable);
  }

  public Object getCellEditorValue() {
    final String cellEditorValue = (String)super.getCellEditorValue();
    return EMPTY.equals(cellEditorValue) ? null : cellEditorValue;
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final List<String> list = myDataFactory.create();
    myData = new HashSet<String>(list);
    String string = (String) value;
    JComboBox comboBox = (JComboBox)editorComponent;
    comboBox.removeAllItems();
    if (myNullable) {
      comboBox.addItem(EMPTY);
    }
    for (final String s : list) {
      comboBox.addItem(s);
    }
    super.getTableCellEditorComponent(table, value, isSelected, row, column);
    if (!myData.contains(string)) {
      comboBox.setEditable(true);
      comboBox.setSelectedItem(string);
      comboBox.setEditable(false);
    }
    return comboBox;
  }
}
