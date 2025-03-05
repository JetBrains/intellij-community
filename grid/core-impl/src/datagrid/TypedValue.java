package com.intellij.database.datagrid;

import com.intellij.database.run.ReservedCellValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypedValue<T> {
  private final ReservedCellValue myValue;

  private final T myType;

  public TypedValue(@NotNull ReservedCellValue value, @NotNull T type) {
    myValue = value;
    myType = type;
  }

  public @NotNull ReservedCellValue getValue() {
    return myValue;
  }

  public @NotNull T getType() {
    return myType;
  }

  public static @Nullable Object unwrap(@Nullable Object value) {
    return value instanceof TypedValue ? ((TypedValue<?>)value).myValue : value;
  }
}
