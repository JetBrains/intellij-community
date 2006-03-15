/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.util.xml.GenericDomValue;

import javax.swing.*;
import javax.swing.table.TableCellEditor;

/**
 * @author peter
 */
public class BooleanColumnInfo extends DomColumnInfo<GenericDomValue<Boolean>, Boolean> {

  public BooleanColumnInfo(final String name) {
    super(name, new BooleanTableCellRenderer());
  }

  public TableCellEditor getEditor(GenericDomValue<Boolean> value) {
    return new DefaultCellEditor(new JCheckBox());
  }

  public final Class<Boolean> getColumnClass() {
    return Boolean.class;
  }

  public final void setValue(final GenericDomValue<Boolean> o, final Boolean aValue) {
    o.setValue(aValue);
  }

  public final Boolean valueOf(GenericDomValue<Boolean> object) {
    final Boolean value = object.getValue();
    return value == null ? Boolean.FALSE : value;
  }
}
