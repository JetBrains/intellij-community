package com.intellij.database.datagrid;

import com.intellij.database.data.types.SizeProvider;
import com.intellij.database.datagrid.mutating.ColumnDescriptor;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

public interface JdbcColumnDescriptor extends ColumnDescriptor, SizeProvider {
  @NlsSafe @Nullable String getJavaClassName();
}
