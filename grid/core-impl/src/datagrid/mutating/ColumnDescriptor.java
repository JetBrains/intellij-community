package com.intellij.database.datagrid.mutating;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public interface ColumnDescriptor {
  // For some data sources this type is not reliable. For example, in CSV, the column type detected by the first N rows
  // and possible, for example, that the type of Double column will be Int or the type of String column will be Long.
  int getType();

  @NlsSafe String getName();

  @NlsSafe @Nullable String getTypeName();

  default @NotNull Set<Attribute> getAttributes() {
    return Collections.emptySet();
  }

  enum Attribute {
    ZERO_PADDING,
    ROW_ID,
    HIDDEN,
    HIGHLIGHTED,
    VIRTUAL
  }
}
