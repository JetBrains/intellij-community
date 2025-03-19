package com.intellij.database.datagrid;

public final class RowSortOrder<T> {

  public enum Type {
    ASC, DESC, UNSORTED
  }

  private final T myColumn;
  private final Type myOrder;

  private RowSortOrder(T column, Type order) {
    myColumn = column;
    myOrder = order;
  }

  public T getColumn() {
    return myColumn;
  }

  public Type getOrder() {
    return myOrder;
  }

  public static <T> RowSortOrder<T> asc(T column) {
    return new RowSortOrder<>(column, Type.ASC);
  }

  public static <T> RowSortOrder<T> desc(T column) {
    return new RowSortOrder<>(column, Type.DESC);
  }

}
