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
public abstract class StripeColumnInfo<T, Aspect> extends ColumnInfo<T, Aspect> {
  private final StripeTableCellRenderer myRenderer;

  public StripeColumnInfo(String name) {
    this(name, new DefaultTableCellRenderer());
  }

  public StripeColumnInfo(String name, final TableCellRenderer renderer) {
    super(name);
    myRenderer = new StripeTableCellRenderer(renderer);
  }

  public boolean isCellEditable(final T o) {
    return getEditor(o) != null;
  }

  public StripeTableCellRenderer getRenderer(T value) {
    return myRenderer;
  }

}
