/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.ui.ColumnInfo;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * @author peter
 */
public abstract class DomColumnInfo<T, Aspect> extends ColumnInfo<T, Aspect> {
  private final TableCellRenderer myRenderer;

  public DomColumnInfo(String name) {
    this(name, new DefaultTableCellRenderer());
  }

  public DomColumnInfo(String name, final TableCellRenderer renderer) {
    super(name);
    myRenderer = renderer;
  }

  public boolean isCellEditable(final T o) {
    return getEditor(o) != null;
  }

  public TableCellRenderer getRenderer(T value) {
    return myRenderer;
  }

}
