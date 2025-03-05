package com.intellij.database.datagrid.mutating;

import com.intellij.database.data.types.SizeProvider;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.JdbcColumnDescriptor;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ColumnQueryData implements Comparable<ColumnQueryData>, JdbcColumnDescriptor {
  private final GridColumn myColumn;
  private final Object myObject;

  public ColumnQueryData(@NotNull GridColumn column, @Nullable Object object) {
    myColumn = column;
    myObject = object;
  }

  public @NotNull GridColumn getColumn() {
    return myColumn;
  }

  public @Nullable Object getObject() {
    return myObject;
  }

  @Override
  public @Nullable String getJavaClassName() {
    return myColumn instanceof JdbcColumnDescriptor ? ((JdbcColumnDescriptor)myColumn).getJavaClassName() : null;
  }

  @Override
  public @NotNull Set<Attribute> getAttributes() {
    return myColumn.getAttributes();
  }

  @Override
  public int compareTo(@NotNull ColumnQueryData o) {
    return Integer.compare(myColumn.getColumnNumber(), o.myColumn.getColumnNumber());
  }

  @Override
  public int getType() {
    return myColumn.getType();
  }

  @Override
  public String getName() {
    return myColumn.getName();
  }

  @Override
  public String getTypeName() {
    return myColumn.getTypeName();
  }

  @Override
  public int getSize() {
    return myColumn instanceof SizeProvider ? ((SizeProvider)myColumn).getSize() : -1;
  }

  @Override
  public int getScale() {
    return myColumn instanceof SizeProvider ? ((SizeProvider)myColumn).getScale() : -1;
  }

  @Override
  public int hashCode() {
    return Comparing.hashcode(myColumn, myObject);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ColumnQueryData toCompare)) return false;
    return Comparing.equal(myColumn, toCompare.myColumn) && Comparing.equal(myObject, toCompare.myObject);
  }
}
