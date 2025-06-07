package com.intellij.database.dump;


import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.DataExtractor;
import com.intellij.database.extractors.ExtractionConfig;
import com.intellij.database.util.Out;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DumpRequestImpl extends UserDataHolderBase implements GridDataRequest, DataConsumer {
  private final AsyncPromise<Void> promise = new AsyncPromise<>();
  private final DumpRequestDelegate myDelegate;

  public DumpRequestImpl(@NotNull String query,
                         @Nullable ModelIndexSet<GridColumn> columns,
                         @NotNull DataExtractor extractor,
                         @NotNull Out out,
                         @Nullable String name,
                         @NotNull ExtractionConfig config,
                         @NotNull Consumer<Integer> addRowCount) {
    myDelegate = new DumpRequestDelegate(0, query, columns, extractor, out, name, config, addRowCount);
  }

  @Override
  public void setColumns(@NotNull GridDataRequest.Context context, int subQueryIndex, int resultSetIndex,
                         GridColumn @NotNull [] columns, int firstRowNum) {
    myDelegate.setColumns(context, subQueryIndex, resultSetIndex, columns, firstRowNum);
  }

  @Override
  public void setInReference(@NotNull Context context, @NotNull Object reference) {
    myDelegate.setInReference(context, reference);
  }

  @Override
  public void updateColumns(@NotNull GridDataRequest.Context context, GridColumn @NotNull [] columns) {
    myDelegate.updateColumns(context, columns);
  }

  @Override
  public void setOutReferences(@NotNull Context context, @NotNull Set<Object> references) {
    myDelegate.setOutReferences(context, references);
  }

  @Override
  public void addRows(@NotNull GridDataRequest.Context context, @NotNull List<? extends GridRow> rows) {
    myDelegate.addRows(context, rows);
  }

  @Override
  public void afterLastRowAdded(@NotNull GridDataRequest.Context context, int total) {
    myDelegate.afterLastRowAdded(context, total);
  }

  @Override
  public @NotNull AsyncPromise<Void> getPromise() {
    return promise;
  }
}