package com.intellij.database.run.ui;

import com.intellij.database.datagrid.GridModel;
import org.jetbrains.annotations.NotNull;

public interface MutationSupport<Row, Column> {
  @NotNull
  GridModel<Row, Column> getMutationModel();

  @NotNull
  GridModel<Row, Column> getDataModel();

  default @NotNull GridModel<Row, Column> getModel(@NotNull DataAccessType type) {
    return type.getModel(this);
  }
}
