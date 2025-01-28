package com.intellij.database.dump;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.GridDataRequest.GridDataRequestOwner;
import com.intellij.database.extractors.*;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.actions.DumpSourceNameProvider;
import com.intellij.database.util.Out;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

import java.util.List;

import static com.intellij.database.run.ui.DataAccessType.DATABASE_DATA;

public abstract class GridHandler extends DumpHandler<DataGrid> {

  public GridHandler(@NotNull Project project,
                     @NotNull DataGrid target,
                     @NotNull DumpSourceNameProvider<DataGrid> nameProvider,
                     @NotNull ExtractionHelper manager,
                     @NotNull DataExtractorFactory factory,
                     @NotNull ExtractionConfig config) {
    super(project, JBIterable.of(target), nameProvider, manager, target.getDisplayName(),
          factory, config);
  }

  @Override
  protected @NotNull ExtractorConfig createExtractorConfig(@NotNull DataGrid source, @Nullable Project project) {
    return ExtractorsHelper.getInstance(source).createExtractorConfig(source, source.getObjectFormatter());
  }

  @Override
  protected @NotNull ObjectFormatter getFormatter(@NotNull DataGrid source) {
    return source.getObjectFormatter();
  }

  @Override
  protected @Nullable String getSourceName(int count) {
    return null;
  }

  @Override
  protected @NlsSafe @Nullable String getDatabaseSystemName() {
    DataGrid grid = mySources.first();
    return grid == null ? null : GridHelper.get(grid).getDatabaseSystemName(grid);
  }

  @Override
  protected @Nullable ModelIndexSet<GridColumn> getSelectedColumns(@NotNull DataGrid source) {
    ModelIndexSet<GridColumn> visibleColumns = source.getVisibleColumns();
    return visibleColumns.size() == source.getDataModel(DATABASE_DATA).getColumnCount() ?
           null :
           visibleColumns;
  }

  public static boolean hasTruncatedData(@NotNull List<? extends GridColumn> columns, List<? extends GridRow> rows) {
    for (GridRow row : rows) {
      for (GridColumn column : columns) {
        Object value = column.getValue(row);
        if (value instanceof LobInfo && ((LobInfo<?>)value).isTruncated()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected @Nullable AsyncPromise<Void> run(@NotNull DataGrid source,
                                             @NotNull DataExtractor extractor,
                                             @NotNull Out out,
                                             @NotNull DumpHandlerParameters dumpParameters) {
    DataProducer producer = getProducer(source, dumpParameters.subQueryIndex, dumpParameters.resultSetIndex);
    GridDataRequestOwner owner = getOwner(source);
    return producer != null ? newSimpleRunner(producer, owner, out, dumpParameters).fun(extractor).getPromise() : null;
  }

  protected abstract @NotNull GridDataRequestOwner getOwner(@NotNull DataGrid source);

  protected abstract @Nullable DataProducer getProducer(@NotNull DataGrid source, int subQueryIndex, int resulSetIndex);
}
