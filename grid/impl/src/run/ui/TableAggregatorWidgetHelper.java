package com.intellij.database.run.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.GridWidget;
import com.intellij.database.datagrid.ResultView;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.extractors.DataAggregatorFactory;
import com.intellij.database.extractors.DataExtractorFactories;
import com.intellij.database.extractors.ExtractorsHelper;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import kotlin.Unit;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.intellij.database.datagrid.AggregatorWidget.AGGREGATOR_WIDGET_HELPER_KEY;

public class TableAggregatorWidgetHelper implements GridWidget.GridWidgetHelper {
  private static final char ABBREVIATION_SUFFIX = '\u2026'; // 2026 '...'
  private static final char RETURN_SYMBOL = '\u23ce';
  private static final int MAX_LENGTH = 50;

  private final DataGrid myGrid;
  private Aggregator myAggregator;
  private volatile Job myLoadWidgetAggregatorJob;

  public TableAggregatorWidgetHelper(@NotNull DataGrid grid) {
    myGrid = grid;
    myLoadWidgetAggregatorJob = null;
  }

  public static void install(@NotNull ResultView table, @NotNull DataGrid grid) {
    TableAggregatorWidgetHelper tableAggregatorWidgetHelper = new TableAggregatorWidgetHelper(grid);
    table.getComponent().putClientProperty(AGGREGATOR_WIDGET_HELPER_KEY, tableAggregatorWidgetHelper);
    tableAggregatorWidgetHelper.loadScripts();
  }

  public void loadScripts(){
    List<DataAggregatorFactory> scripts = DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(myGrid), GridUtil::suggestPlugin);
    DataGridSettings settings = GridUtil.getSettings(myGrid);
    String chosenAggregatorName = Objects.requireNonNullElse(settings == null ? null : settings.getWidgetAggregator(), "SUM.groovy");
    for (DataAggregatorFactory script : scripts) {
      if (StringUtil.equals(script.getName(), chosenAggregatorName)) {
        if (myLoadWidgetAggregatorJob != null) {
          myLoadWidgetAggregatorJob.cancel(null);
        }
        Job job = AggregatorWidgetLoader.loadWidgetAggregatorAsync(myGrid, script, this);
        myLoadWidgetAggregatorJob = job;
        job.invokeOnCompletion((Throwable _) -> {
          if (myLoadWidgetAggregatorJob == job) {
            myLoadWidgetAggregatorJob = null;
          }
          return Unit.INSTANCE;
        });
      }
    }
  }

  public void setAggregator(@Nullable Aggregator aggregator) {
    if (myLoadWidgetAggregatorJob != null) {
      myLoadWidgetAggregatorJob.cancel(null);
    }
    myAggregator = aggregator;
  }

  public @Nullable Aggregator getAggregator() {
    return myAggregator;
  }

  @Override
  public @NotNull CompletableFuture<@NlsContexts.Label String> getText() {
    if (myGrid.isEmpty()) {
      return CompletableFuture.completedFuture("");
    }
    @NlsSafe
    @NlsContexts.Label
    StringBuilder sb = new StringBuilder();
    if (myAggregator != null) {
      sb.append(myAggregator.getSimpleName());
      sb.append(": ");
      SelectionModel<GridRow, GridColumn> selectionModel = myGrid.getSelectionModel();
      boolean hasSelectedValues = selectionModel.getSelectedColumnCount() * selectionModel.getSelectedRowCount() != 0;
      if (hasSelectedValues) {
        return myAggregator.update().thenApply(result -> {
          sb.append(result.getText());
          return cleanup(sb.toString());
        });
      }
      else {
        sb.append(DataGridBundle.message("label.aggregator.not.enough.values"));
        return CompletableFuture.completedFuture(sb.toString());
      }
    }
     return CompletableFuture.completedFuture(DataGridBundle.message("label.aggregator.not.chosen"));
  }

  private static @NlsContexts.Label String cleanup(@NotNull @NlsContexts.Label String s) {
    String result = s.replaceAll("\n", String.valueOf(RETURN_SYMBOL));
    return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH) + ABBREVIATION_SUFFIX : result;
  }

  public @NotNull CompletableFuture<String> getResultText() {
    return myAggregator != null ? myAggregator.update().thenApply(res -> res.getText()) : CompletableFuture.completedFuture("");
  }
}
