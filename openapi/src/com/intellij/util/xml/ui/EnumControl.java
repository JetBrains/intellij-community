/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.Function;
import com.intellij.util.xml.NamedEnumUtil;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class EnumControl<T extends Enum> extends BaseControl<JComboBox, String> {
  private final Class<T> myClass;
  private static final Function<Enum, String> STANDARD_SHOW = new Function<Enum, String>() {
    public String fun(final Enum s) {
      return NamedEnumUtil.getEnumValueByElement(s);
    }
  };

  public EnumControl(final DomWrapper<String> domWrapper, final Class<T> aClass) {
    super(domWrapper);
    myClass = aClass;
  }

  protected final JComboBox createMainComponent() {
    return createEnumComboBox(myClass);
  }

  public static <T extends Enum> DefaultCellEditor createTableCellEditor(final Class<T> type) {
    final DefaultCellEditor defaultCellEditor = new DefaultCellEditor(createEnumComboBox(type));
    defaultCellEditor.setClickCountToStart(2);
    return defaultCellEditor;
  }

  public static <T extends Enum> JComboBox createEnumComboBox(final Class<T> type) {
    return tuneUpComboBox(type, new JComboBox(), (Function<T, String>)STANDARD_SHOW);
  }

  private static <T extends Enum>JComboBox tuneUpComboBox(final Class<T> type, final JComboBox comboBox, Function<T,String> show) {
    final T[] enumConstants = type.getEnumConstants();
    final Set<Object> standardValues = new HashSet<Object>();
    for (T enumConstant : enumConstants) {
      final String s = show.fun(enumConstant);
      comboBox.addItem(s);
      standardValues.add(s);
    }
    comboBox.setEditable(false);
    comboBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!standardValues.contains(value)) {
          setFont(getFont().deriveFont(Font.ITALIC));
          setForeground(Color.RED);
        }
        return this;
      }
    });
    return comboBox;
  }

  protected JComboBox createMainComponent(final JComboBox boundedComponent) {
    final JComboBox comboBox;
    if (boundedComponent == null) {
      comboBox = new JComboBox();
    } else {
      comboBox = boundedComponent;
      comboBox.removeAllItems();
    }

    return tuneUpComboBox(myClass, comboBox, getShow());
  }

  protected Function<T,String> getShow() {
    return (Function<T, String>)STANDARD_SHOW;
  }

  protected final String getValue(final JComboBox component) {
    final int index = component.getSelectedIndex();
    return index < 0
           ? (String)component.getSelectedItem()
           : NamedEnumUtil.getEnumValueByElement(myClass.getEnumConstants()[index]);
  }

  protected final void setValue(final JComboBox component, final String value) {
    final T t = NamedEnumUtil.getEnumElementByValue(myClass, value);
    if (t != null) {
      component.setSelectedItem(getShow().fun(t));
    } else {
      component.setEditable(true);
      component.setSelectedItem(value);
      component.setEditable(false);
    }
  }
}
