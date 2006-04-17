/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.Function;
import com.intellij.util.containers.*;
import com.intellij.util.xml.NamedEnumUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Condition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.HashSet;

/**
 * @author peter
 */
public class ComboControl extends BaseControl<JComboBox, String> {
  private final Factory<List<String>> myDataFactory;
  private Set<String> mySet = new com.intellij.util.containers.HashSet<String>();
  private final ActionListener myCommitListener = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      commit();
    }
  };

  public ComboControl(final GenericDomValue genericDomValue, final Factory<List<String>> dataFactory) {
    this(new DomStringWrapper(genericDomValue), dataFactory);
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Factory<List<String>> dataFactory) {
    super(domWrapper);
    myDataFactory = dataFactory;
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Class<? extends Enum> aClass) {
    super(domWrapper);
    myDataFactory = createEnumFactory(aClass);
  }

  static Factory<List<String>> createEnumFactory(final Class<? extends Enum> aClass) {
    return new Factory<List<String>>() {
      public List<String> create() {
        return ContainerUtil.map2List(aClass.getEnumConstants(), new Function<Enum, String>() {
          public String fun(final Enum s) {
            return NamedEnumUtil.getEnumValueByElement(s);
          }
        });
      }
    };
  }

  public static <T extends Enum> JComboBox createEnumComboBox(final Class<T> type) {
    return tuneUpComboBox(new JComboBox(), createEnumFactory(type));
  }

  private static JComboBox tuneUpComboBox(final JComboBox comboBox, Factory<List<String>> dataFactory) {
    final List<String> list = dataFactory.create();
    final Set<String> standardValues = new HashSet<String>();
    for (final String s : list) {
      comboBox.addItem(s);
      standardValues.add(s);
    }
    return initComboBox(comboBox, new Condition<String>() {
      public boolean value(final String object) {
        return standardValues.contains(object);
      }
    });
  }

  static JComboBox initComboBox(final JComboBox comboBox, final Condition<String> validity) {
    comboBox.setEditable(false);
    comboBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!validity.value((String)value)) {
          setFont(getFont().deriveFont(Font.ITALIC));
          setForeground(Color.RED);
        }
        return this;
      }
    });
    return comboBox;
  }

  protected JComboBox createMainComponent(final JComboBox boundedComponent) {
    return tuneUpComboBox(boundedComponent == null ? new JComboBox() : boundedComponent, myDataFactory);
  }

  public boolean isValidValue(final String object) {
    return mySet.contains(object);
  }

  protected void doReset() {
    final JComboBox comboBox = getComponent();
    comboBox.removeActionListener(myCommitListener);
    final String oldSelected = (String)comboBox.getSelectedItem();
    final List<String> data = myDataFactory.create();
    mySet.clear();
    comboBox.removeAllItems();
    for (final String s : data) {
      comboBox.addItem(s);
      mySet.add(s);
    }
    setValue(oldSelected);
    super.doReset();
    comboBox.addActionListener(myCommitListener);
  }

  protected final String getValue() {
    return (String)getComponent().getSelectedItem();
  }

  protected final void setValue(final String value) {
    final JComboBox component = getComponent();
    if (!isValidValue(value)) {
      component.setEditable(true);
    }
    component.setSelectedItem(value);
    component.setEditable(false);
  }
}
