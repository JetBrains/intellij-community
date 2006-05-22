/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.GenericDomValue;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * @author peter
 */
public class GenericValueColumnInfo<T> extends DomColumnInfo<GenericDomValue<T>, String> {
  private final Class<T> myColumnClass;
  private final TableCellEditor myEditor;

  public GenericValueColumnInfo(final String name, final Class<T> columnClass, final TableCellRenderer renderer, final TableCellEditor editor) {
    super(name, renderer);
    myColumnClass = columnClass;
    myEditor = editor;
  }

  public GenericValueColumnInfo(final String name, final Class<T> columnClass, final TableCellEditor editor) {
    this(name, columnClass, new DefaultTableCellRenderer(), editor);
  }

  public final TableCellEditor getEditor(GenericDomValue<T> value) {
    return myEditor;
  }

  public final Class<T> getColumnClass() {
    return myColumnClass;
  }

  public final void setValue(final GenericDomValue<T> o, final String aValue) {
    o.setStringValue(aValue);
  }

  public final String valueOf(GenericDomValue<T> object) {
    return object.getStringValue();
  }
}
