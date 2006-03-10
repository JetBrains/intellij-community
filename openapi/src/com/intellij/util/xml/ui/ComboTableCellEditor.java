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

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Condition;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * @author peter
 */
public class ComboTableCellEditor extends DefaultCellEditor {
  private final Factory<List<String>> myDataFactory;
  private Set<String> myData;

  public ComboTableCellEditor(Factory<List<String>> dataFactory) {
    super(new JComboBox());
    myDataFactory = dataFactory;
    setClickCountToStart(2);
    JComboBox comboBox = (JComboBox)editorComponent;
    ComboControl.initComboBox(comboBox, new Condition<String>() {
      public boolean value(final String object) {
        return myData != null && myData.contains(object);
      }
    });
  }

  public ComboTableCellEditor(Class<? extends Enum> anEnum) {
    this(ComboControl.createEnumFactory(anEnum));
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    myData = new HashSet<String>(myDataFactory.create());
    String string = (String) value;
    final JComboBox comboBox = (JComboBox) super.getTableCellEditorComponent(table, value, isSelected, row, column);
    if (!myData.contains(string)) {
      comboBox.setEditable(true);
      comboBox.setSelectedItem(string);
      comboBox.setEditable(false);
    }
    return comboBox;
  }
}
