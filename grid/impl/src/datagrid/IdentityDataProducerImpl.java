package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IdentityDataProducerImpl extends IdentityDataProducer {

  public IdentityDataProducerImpl(DataConsumer consumer,
                                  List<? extends GridColumn> columns,
                                  List<? extends GridRow> rows,
                                  int subQueryIndex,
                                  int resultSetIndex) {
    super(consumer, columns, rows, subQueryIndex, resultSetIndex);
  }

  @Override
  protected @NotNull GridDataRequest.Context createContext(@NotNull GridDataRequest r) {
    return new EmptyContext();
  }
}
