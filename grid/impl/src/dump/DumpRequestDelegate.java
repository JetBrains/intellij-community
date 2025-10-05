package com.intellij.database.dump;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.DataExtractor;
import com.intellij.database.extractors.DefaultValuesExtractor;
import com.intellij.database.extractors.ExtractionConfig;
import com.intellij.database.extractors.FormatBasedExtractor;
import com.intellij.database.util.Out;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class DumpRequestDelegate implements DataConsumer {
  private static final Logger LOG = Logger.getInstance(DumpRequestDelegate.class);
  private final int myResultSetNumber;
  private final String myQuery;
  private final ProgressIndicator myProgressIndicator;
  private final String myName;
  private final ExtractionConfig myConfig;
  private final Consumer<Integer> myAddRowCount;
  private final DataExtractor myExtractor;
  private final Out myOut;
  private final ModelIndexSet<GridColumn> mySelectedColumns;

  private int myFirstRowNum;
  protected GridColumn[] myColumns;
  private DataExtractor.Extraction myExtraction;

  public DumpRequestDelegate(int resultSetNumber,
                             @NotNull String query,
                             @Nullable ModelIndexSet<GridColumn> columns,
                             @NotNull DataExtractor extractor,
                             @NotNull Out out,
                             @Nullable String name,
                             @NotNull ExtractionConfig config,
                             @NotNull Consumer<Integer> addRowCount) {
    myResultSetNumber = resultSetNumber;
    myQuery = query;
    myProgressIndicator = ObjectUtils.chooseNotNull(ProgressManager.getInstance().getProgressIndicator(), new EmptyProgressIndicator());
    mySelectedColumns = columns;
    myExtractor = extractor;
    myOut = out;
    myName = name;
    myConfig = config;
    myAddRowCount = addRowCount;
  }

  @Override
  public void setColumns(GridDataRequest.@NotNull Context context, int subQueryIndex, int resultSetIndex,
                         GridColumn @NotNull [] columns, int firstRowNum) {
    LOG.assertTrue(resultSetIndex == myResultSetNumber);
    myColumns = columns;
    myFirstRowNum = firstRowNum;
    myProgressIndicator.setText(getSavingText());
  }

  private @NlsContexts.ProgressText @NotNull String getSavingText() {
    return DataGridBundle.message("progress.text.saving.choice", myName, myName == null ? 0 : 1);
  }

  @Override
  public void updateColumns(GridDataRequest.@NotNull Context context, GridColumn @NotNull [] columns) {
    if (myExtraction == null) {
      myColumns = columns;
    }
    else {
      myExtraction.updateColumns(columns);
    }
  }

  @Override
  public void addRows(GridDataRequest.@NotNull Context context, @NotNull List<? extends GridRow> rows) {
    if (myExtraction == null) myExtraction = startExtraction();
    myProgressIndicator.checkCanceled();
    if (rows.isEmpty()) return;
    ObjectNormalizer.convertRows(ObjectNormalizerProvider.getCache().fun(context), rows, Arrays.stream(myColumns).toList());
    // todo handle raw blob / clob
    int maxRow = rows.get(rows.size() - 1).getRowNum();
    if (rows.get(0).getRowNum() > myFirstRowNum &&
        myExtractor instanceof DefaultValuesExtractor &&
        !(myExtractor instanceof FormatBasedExtractor)) {
      myOut.appendText(((DefaultValuesExtractor)myExtractor).getLineSeparator());
    }
    myExtraction.addData(rows);
    String bytesLoadedMessagePart = " / " + StringUtil.formatFileSize(myOut.sizeInBytes()) + DataGridBundle.message("progress.text.rows.chars.loaded");
    myProgressIndicator.setText(
      DataGridBundle.message("progress.text.rows",
                             getSavingText(),
                             maxRow,
                             bytesLoadedMessagePart)
    );
    myAddRowCount.accept(rows.size());
  }

  @Override
  public void afterLastRowAdded(GridDataRequest.@NotNull Context context, int total) {
    if (myExtraction == null) myExtraction = startExtraction();
    myExtraction.complete();
  }

  private @NotNull DataExtractor.Extraction startExtraction() {
    int[] selectedColumns = mySelectedColumns != null ? mySelectedColumns.asArray() : ArrayUtilRt.EMPTY_INT_ARRAY;

    List<GridColumn> columns = myColumns != null ? Arrays.asList(myColumns) : Collections.emptyList();
    return myExtractor.startExtraction(myOut, columns, myQuery, myConfig, selectedColumns);
  }

  public GridColumn[] getColumns() {
    return myColumns;
  }
}
