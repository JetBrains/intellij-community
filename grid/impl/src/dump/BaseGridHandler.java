package com.intellij.database.dump;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.DataExtractor;
import com.intellij.database.extractors.DataExtractorFactory;
import com.intellij.database.extractors.ExtractionConfig;
import com.intellij.database.run.actions.DumpSourceNameProvider;
import com.intellij.database.util.Out;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

import static com.intellij.database.run.ui.DataAccessType.DATABASE_DATA;

public abstract class BaseGridHandler extends GridHandler {

  public BaseGridHandler(@NotNull Project project,
                         @NotNull DataGrid target,
                         @NotNull DumpSourceNameProvider<DataGrid> nameProvider,
                         @NotNull ExtractionHelper manager,
                         @NotNull DataExtractorFactory factory,
                         @NotNull ExtractionConfig config) {
    super(project, target, nameProvider, manager, factory, config);
  }

  @Override
  protected int getSubQueryIndex(@NotNull DataGrid source) {
    return 0;
  }

  @Override
  protected int getResultSetIndex(@NotNull DataGrid source) {
    return 0;
  }

  @Override
  protected @NotNull GridDataRequest.GridDataRequestOwner getOwner(@NotNull DataGrid source) {
    return new GridDataRequest.GridDataRequestOwner() {
      @Override
      public @NotNull @Nls String getDisplayName() {
        @NlsSafe String owner = "Owner";
        return owner;
      }

      @Override
      public void dispose() {
      }
    };
  }

  @Override
  protected @Nullable DataProducer getProducer(@NotNull DataGrid source, int subQueryIndex, int resulSetIndex) {
    GridPagingModel<GridRow, GridColumn> pageModel = source.getDataHookup().getPageModel();
    GridModel<GridRow, GridColumn> model = source.getDataModel(DATABASE_DATA);
    if (pageModel.isFirstPage() && pageModel.isLastPage() && !hasTruncatedData(model.getColumns(), model.getRows())) {
      return new IdentityDataProducerImpl(new DataConsumer.Composite(),
                                          model.getColumns(),
                                          new ArrayList<>(model.getRows()),
                                          0,
                                          0);
    }
    return createProducer(source, resulSetIndex);
  }

  protected abstract DataProducer createProducer(@NotNull DataGrid grid, int index);

  @Override
  protected @Nullable String getProducerName() {
    return null;
  }

  @Override
  protected @NotNull GridDataRequest createDumpRequest(@NotNull GridDataRequest.GridDataRequestOwner owner,
                                                       DataExtractor extractor,
                                                       @NotNull Out out,
                                                       @Nullable String name,
                                                       @NotNull DumpHandlerParameters dumpParameters) {
    return new DumpRequestImpl(dumpParameters.queryText, dumpParameters.selectedColumns, extractor,
                               Objects.requireNonNull(out), name, myConfig, size -> myRowsCount += size);
  }
}
