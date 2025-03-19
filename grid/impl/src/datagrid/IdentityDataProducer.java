package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
* @author gregsh
*/
public abstract class IdentityDataProducer implements DataProducer {
  private final DataConsumer myConsumer;
  private final List<? extends GridColumn> myColumns;
  private final List<? extends GridRow> myRows;
  private final int mySubQueryIndex;
  private final int myResultSetIndex;

  public IdentityDataProducer(DataConsumer consumer, List<? extends GridColumn> columns, List<? extends GridRow> rows,
                              int subQueryIndex, int resultSetIndex) {
    myConsumer = consumer;
    myColumns = columns;
    myRows = rows;
    mySubQueryIndex = subQueryIndex;
    myResultSetIndex = resultSetIndex;
  }

  @Override
  public void processRequest(@NotNull GridDataRequest r) {
    DataConsumer consumer = r instanceof DataConsumer ?
                            new DataConsumer.Composite(Arrays.asList(myConsumer, (DataConsumer)r)) :
                            myConsumer;
    GridDataRequest.Context context = createContext(r);
    if (context == null) {
      r.getPromise().setError("Cannot create request context. GridDataRequest: " + r);
      return;
    }
    int rowNum = myRows.isEmpty() ? 0 : myRows.get(0).getRowNum();

    try {
      consumer.setColumns(context, mySubQueryIndex, myResultSetIndex, myColumns.toArray(new GridColumn[0]), rowNum);
      doAddRows(context, consumer, myRows);
      consumer.afterLastRowAdded(context, myRows.size());
      r.getPromise().setResult(null);
    }
    catch (Exception e) {
      r.getPromise().setError(e);
    }
  }

  protected abstract @Nullable GridDataRequest.Context createContext(@NotNull GridDataRequest r);

  protected void doAddRows(GridDataRequest.Context context, DataConsumer handler, List<? extends GridRow> rows) {
    handler.addRows(context, rows);
  }
}
