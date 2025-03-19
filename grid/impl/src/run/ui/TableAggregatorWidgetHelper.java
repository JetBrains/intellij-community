package com.intellij.database.run.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.*;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
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

  public TableAggregatorWidgetHelper(@NotNull ResultView table, @NotNull DataGrid grid) {
    myGrid = grid;
    List<DataAggregatorFactory> scripts = DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(myGrid), GridUtil::suggestPlugin);
    DataGridSettings settings = GridUtil.getSettings(grid);
    String chosenAggregatorName = Objects.requireNonNullElse(settings == null ? null : settings.getWidgetAggregator(), "SUM.groovy");
    table.getComponent().putClientProperty(AGGREGATOR_WIDGET_HELPER_KEY, this);
    for (DataAggregatorFactory script : scripts) {
      if (StringUtil.equals(script.getName(), chosenAggregatorName)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          ExtractorConfig config = ExtractorsHelper.getInstance(grid).createExtractorConfig(grid, grid.getObjectFormatter());
          DataExtractor extractor = script.createAggregator(config);
          if (extractor == null) return;
          myAggregator = new Aggregator(grid, extractor, script.getSimpleName(), script.getName());
        });
      }
    }
  }


  public void setAggregator(@Nullable Aggregator aggregator) {
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
