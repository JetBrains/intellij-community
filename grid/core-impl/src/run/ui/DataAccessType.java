package com.intellij.database.run.ui;

import com.intellij.database.datagrid.GridModel;
import org.jetbrains.annotations.NotNull;

public enum DataAccessType {
  DATA_WITH_MUTATIONS {
    @Override
    public @NotNull <Row, Column> GridModel<Row, Column> getModel(@NotNull MutationSupport<Row, Column> support) {
      return support.getMutationModel();
    }
  },
  DATABASE_DATA {
    @Override
    public @NotNull <Row, Column> GridModel<Row, Column> getModel(@NotNull MutationSupport<Row, Column> support) {
      return support.getDataModel();
    }
  };

  public abstract @NotNull <Row, Column> GridModel<Row, Column> getModel(@NotNull MutationSupport<Row, Column> support);
}
