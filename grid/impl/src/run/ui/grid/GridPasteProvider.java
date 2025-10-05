package com.intellij.database.run.ui.grid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.ui.CsvRecordFormatForm;
import com.intellij.database.data.types.BaseConversionGraph;
import com.intellij.database.data.types.DataTypeConversion;
import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.run.actions.ChoosePasteFormatAction;
import com.intellij.database.run.actions.ChoosePasteFormatAction.PasteType;
import com.intellij.ide.PasteProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.database.csv.ui.CsvRecordFormatForm.DELIMITERS;
import static com.intellij.database.datagrid.GridUtil.addRows;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public class GridPasteProvider implements PasteProvider {
  private static final Logger LOG = Logger.getInstance(GridPasteProvider.class);
  private final DataGrid myGrid;
  private final TableDataParser myTableDataParser;

  public GridPasteProvider(@NotNull DataGrid grid, @Nullable TableDataParser tableDataParser) {
    myGrid = grid;
    myTableDataParser = tableDataParser;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    @NotNull Project project = myGrid.getProject();
    Object contents = CopyPasteManager.getInstance().getContents(GridTransferableData.ourFlavor);
    GridTransferableData data = null;
    PasteType pasteType = null;
    CsvFormat csvFormat = null;
    if (contents instanceof GridTransferableData d) {
      data = d;
    }
    else {
      contents = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
      if (contents instanceof String text) {
        TableDataParseResult result = myTableDataParser == null ? null : myTableDataParser.retrieveDataFromText(project, text, myGrid);
        data = result == null ? null : result.myData;
        pasteType = result == null ? null : result.myPasteType;
        csvFormat = result == null ? null : result.myFormat;
      }
    }
    if (data == null) return;
    SelectionModel<GridRow, GridColumn> model = myGrid.getSelectionModel();
    ModelIndexSet<GridRow> rows = model.getSelectedRows();
    ModelIndexSet<GridColumn> columns = model.getSelectedColumns();
    data = adjustDataToSelection(data, rows.size(), true);
    data = adjustDataToSelection(data, columns.size(), false);
    int minRow = Math.max(0, GridUtil.min(rows.toView(myGrid)));
    int maxRow = minRow + data.getRowsCount() - 1;
    int minCol = Math.max(0, GridUtil.min(columns.toView(myGrid)));
    int rowOffset = minRow - data.getFirstRowIdx();
    int colOffset = minCol - data.getFirstColumnIdx();
    int count = myGrid.getDataModel(DATA_WITH_MUTATIONS).getRowCount();
    addRows(myGrid, Math.max(0, maxRow - count + 1));

    List<Exception> conversionExceptions = new ArrayList<>();

    List<CellMutation.Builder> conversions = JBIterable.from(data.getConversions())
      .map(conversion -> conversion
        .copy()
        .secondGrid(myGrid)
        .offset(rowOffset, colOffset)
        .build())
      .filter(DataTypeConversion::isValid)
      .map(c -> {
        try {
          return GridHelper.get(myGrid).isMixedTypeColumns(myGrid)
                 ? c.build()
                 : c.convert(BaseConversionGraph.get(myGrid));
        } catch (Exception e) {
          conversionExceptions.add(e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .toList();

    myGrid.getDataSupport().finishBuildingAndApply(conversions);
    if (!conversionExceptions.isEmpty()) {
      String error = conversionExceptions.size() == 1
                     ? conversionExceptions.get(0).getMessage()
                     : DataGridBundle.message("group.Console.TableResult.PasteError.multiple.text", conversionExceptions.size());
      Notification errorNotification = DataGridNotifications.PASTE_GROUP
        .createNotification(DataGridBundle.message("group.Console.TableResult.PasteError.title"),
                            error,
                            NotificationType.ERROR);
      errorNotification.notify(myGrid.getProject());
    } else {
      if (pasteType != null) {
        Notification notification = createNotification(pasteType, csvFormat);
        if (notification != null) {
          notification.addAction(
              new ChoosePasteFormatAction(DataGridBundle.message("group.Console.TableResult.PasteFormat.change.paste.format")))
            .notify(myGrid.getProject());
        }
      }
    }
  }

  private static @Nullable Notification createNotification(@NotNull PasteType pasteType, @Nullable CsvFormat csvFormat) {
    if (pasteType == PasteType.SINGLE_VALUE) {
      return DataGridNotifications.PASTE_GROUP
        .createNotification(DataGridBundle.message("table.data.pasted"),
                            DataGridBundle.message("table.data.text.pasted.as.single.value"),
                            NotificationType.INFORMATION);
    }
    else if (pasteType == PasteType.FORMAT) {
      if (csvFormat == null) {
        LOG.error("Paste type is FORMAT but csv format is null.");
        return null;
      }
      return DataGridNotifications.PASTE_GROUP
        .createNotification(DataGridBundle.message("table.data.pasted"),
                            DataGridBundle.message("table.text.pasted.using.format", csvFormat.name, getDelimiterName(csvFormat)),
                            NotificationType.INFORMATION);
    }
    else if (pasteType == PasteType.AUTO) {
      if (csvFormat == null) {
        return DataGridNotifications.PASTE_GROUP
          .createNotification(DataGridBundle.message("table.data.pasted"),
                              DataGridBundle.message("table.data.no.format.detected"),
                              NotificationType.INFORMATION);
      }
      else {
        return DataGridNotifications.PASTE_GROUP
          .createNotification(DataGridBundle.message("table.data.pasted"),
                              DataGridBundle.message("table.data.format.detected", csvFormat.name, getDelimiterName(csvFormat)),
                              NotificationType.INFORMATION);
      }
    }
    return null;
  }

  public static @NotNull @NlsSafe String getDelimiterName(@NotNull CsvFormat format) {
    return getDelimiterName(format.dataRecord.valueSeparator);
  }

  public static @NotNull @NlsSafe String getDelimiterName(@NotNull String delimiter) {
    CsvRecordFormatForm.LazyPair<String> pair = ContainerUtil.find(DELIMITERS, d -> d.value.equals(delimiter));
    return pair != null
           ? pair.uiNameLowercase != null ? pair.uiNameLowercase.get() : pair.uiName.get()
           : "'" + delimiter + "'";
  }

  private static @NotNull GridTransferableData adjustDataToSelection(@NotNull GridTransferableData data, int selectedCount, boolean isRow) {
    int dataCount = isRow ? data.getRowsCount() : data.getColumnsCount();
    int duplicateCount = selectedCount / dataCount;
    if (duplicateCount <= 1) return data;
    List<DataTypeConversion.Builder> newConversions = new ArrayList<>(data.getConversions());
    for (int i = 1; i < duplicateCount; i++) {
      for (DataTypeConversion.Builder conversion : data.getConversions()) {
        newConversions.add(
          isRow
          ? conversion.copy().firstRowIdx(conversion.firstRowIdx() + i * dataCount)
          : conversion.copy().firstColumnIdx(conversion.firstColumnIdx() + i * dataCount)
        );
      }
    }
    return new GridTransferableData(newConversions, data.getTransferable(), data.getFirstRowIdx(), data.getFirstColumnIdx(),
                                    isRow ? dataCount * duplicateCount : dataCount);
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    CopyPasteManager manager = CopyPasteManager.getInstance();
    return isPasteEnabled(dataContext) &&
           (manager.getContents(GridTransferableData.ourFlavor) instanceof GridTransferableData ||
            manager.getContents(DataFlavor.stringFlavor) != null);
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return myGrid.isReady() && myGrid.isEditable();
  }

  public interface TableDataParser {
    @NotNull
    TableDataParseResult retrieveDataFromText(@NotNull Project project, @NotNull String text, @NotNull DataGrid grid);
  }

  public static class TableDataParseResult {
    public final GridTransferableData myData;
    public final PasteType myPasteType;
    public final CsvFormat myFormat;

    public TableDataParseResult(@NotNull GridTransferableData data,
                                @NotNull PasteType type,
                                @Nullable CsvFormat format) {
      myData = data;
      myPasteType = type;
      myFormat = format;
    }
  }
}
