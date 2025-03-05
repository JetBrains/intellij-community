package com.intellij.database.run.ui.grid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.data.types.DataTypeConversion;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.*;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.settings.BytesLimitPerValueForm;
import com.intellij.database.settings.CsvSettings;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GridCopyProvider implements CopyProvider {
  private final DataGrid myGrid;

  public GridCopyProvider(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    DataExtractor extractor = createExtractor();
    String data = GridUtil.extractSelectedValuesForCopy(myGrid, extractor);
    boolean htmlNeeded = extractor.getFileExtension().contains("htm");
    Transferable content = htmlNeeded ? new TextTransferable(data) : new StringSelection(data);
    CopyPasteManager.getInstance().setContents(createData(content));
    if (truncatedDataCopied()) {
      showTruncatedDataCopiedWarning();
    }

    GridUtil.activeGridListener().onExtractToClipboardAction(myGrid);
  }

  private void showTruncatedDataCopiedWarning() {
    Pair<RelativePoint, Balloon.Position> position = GridUtil.getBestPositionForBalloon(myGrid);

    DataGridSettings settings = GridUtil.getSettings(myGrid);
    Balloon balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(DataGridBundle.message("popup.content.truncated.data.copied", settings == null ? 0 : 1), MessageType.WARNING,
                                    settings == null ? null : new ChangeLimitHyperlinkListener(myGrid, settings))
      .setShowCallout(true)
      .setHideOnAction(true)
      .setHideOnClickOutside(true)
      .setHideOnLinkClick(true)
      .createBalloon();

    balloon.show(position.first, position.second);
  }

  private boolean truncatedDataCopied() {
    List<GridRow> rows = GridUtil.getSelectedGridRows(myGrid);
    GridModel<GridRow, GridColumn> model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    ModelIndexSet<GridColumn> columnIndexes = myGrid.getSelectionModel().getSelectedColumns();
    List<GridColumn> columns = model.getColumns(columnIndexes);
    return ContainerUtil.find(rows, row -> ContainerUtil.find(columns, column -> isTruncated(column.getValue(row))) != null) != null;
  }

  private static boolean isTruncated(@Nullable Object value) {
    return value instanceof LobInfo && ((LobInfo<?>)value).isTruncated();
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return !myGrid.isEditing() && !myGrid.isEmpty();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  private @NotNull DataExtractor createExtractor() {
    SelectionModel<GridRow, GridColumn> selectionModel = myGrid.getSelectionModel();
    if (selectionModel.getSelectedRowCount() == 1 &&
        selectionModel.getSelectedColumnCount() == 1) {
      return GridExtractorsUtilCore.getSingleValueExtractor(myGrid.getObjectFormatter(), GridUtil.getConfigProvider(myGrid, true));
    }

    DataExtractorFactory extractorFactory = DataExtractorFactories.getExtractorFactory(myGrid, GridUtil::suggestPlugin);
    ExtractorConfig config = ExtractorsHelper.getInstance(myGrid).createExtractorConfig(myGrid, myGrid.getObjectFormatter());
    DataExtractor extractor = extractorFactory.createExtractor(config);
    extractor = extractor != null ? extractor : DataExtractorFactories.getDefault(CsvSettings.getSettings())
      .createExtractor(config);
    return Objects.requireNonNull(extractor);
  }

  private @NotNull Transferable createData(@NotNull Transferable content) {
    SelectionModel<GridRow, GridColumn> model = myGrid.getSelectionModel();
    ViewIndexSet<GridRow> rows = model.getSelectedRows().toView(myGrid);
    ViewIndexSet<GridColumn> columns = model.getSelectedColumns().toView(myGrid);
    if (rows.size() == 0 || columns.size() == 0) return content;

    GridModel<GridRow, GridColumn> dataModel = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    int minRow = GridUtil.min(rows);
    int minColumn = GridUtil.min(columns);
    GridHelper helper = GridHelper.get(myGrid);
    List<DataTypeConversion.Builder> result = new ArrayList<>();
    for (ViewIndex<GridRow> row : rows.asIterable()) {
      for (ViewIndex<GridColumn> columnIdx : columns.asIterable()) {
        Object value = dataModel.getValueAt(row.toModel(myGrid), columnIdx.toModel(myGrid));
        GridColumn column = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx.toModel(myGrid));
        result.add(
          helper.createDataTypeConversionBuilder()
            .value(value instanceof ReservedCellValue ? null : value)
            .firstColumn(Objects.requireNonNull(column))
            .firstRowIdx(row.asInteger())
            .firstColumnIdx(columnIdx.asInteger())
            .firstGrid(myGrid)
        );
      }
    }
    return new GridTransferableData(result, content, minRow, minColumn, rows.size());
  }

  public static class ChangeLimitHyperlinkListener implements HyperlinkListener {
    private final DataGrid myGrid;
    private final DataGridSettings mySettings;

    public ChangeLimitHyperlinkListener(@NotNull DataGrid grid, @NotNull DataGridSettings settings) {
      myGrid = grid;
      mySettings = settings;
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if ("change_limit".equals(e.getDescription())) {
          new ChangeBytesLimitPerValueDialogWrapper(myGrid.getProject(), myGrid, mySettings).show();
        }
      }
    }
  }

  private static class ChangeBytesLimitPerValueDialogWrapper extends DialogWrapper {
    private final BytesLimitPerValueForm myForm = new BytesLimitPerValueForm();
    private final DataGrid myGrid;
    private final DataGridSettings mySettings;
    private final ApplyAction myApplyAction = new ApplyAction();

    protected ChangeBytesLimitPerValueDialogWrapper(@Nullable Project project,
                                                    @NotNull DataGrid grid,
                                                    @NotNull DataGridSettings settings) {
      super(project, grid.getPanel().getComponent(), false, IdeModalityType.IDE);
      myGrid = grid;
      mySettings = settings;

      setTitle(DataGridBundle.message("dialog.title.change.bytes.limit"));
      setOKButtonText(DataGridBundle.message("dialog.ok.text.set.and.reload"));
      initListeners();

      init();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myForm.getField();
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction(), getCancelAction(), myApplyAction};
    }

    private void initListeners() {
      myForm.getField().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          updateActions();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          updateActions();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          updateActions();
        }
      });
    }

    private void updateActions() {
      getOKAction().setEnabled(isOKActionEnabled());
      myApplyAction.setEnabled(isOKActionEnabled() && myForm.isModified(mySettings));
    }

    @Override
    public boolean isOKActionEnabled() {
      try {
        myForm.getField().validateContent();
        return true;
      }
      catch (ConfigurationException ignored) {
        return false;
      }
    }

    @Override
    protected @NotNull JComponent createCenterPanel() {
      myForm.reset(mySettings);
      return myForm.getPanel();
    }

    @Override
    protected void doOKAction() {
      super.doOKAction();
      myForm.apply(mySettings);
      mySettings.fireChanged();
      GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(myGrid));
      myGrid.getDataHookup().getLoader().reloadCurrentPage(source);
    }

    private class ApplyAction extends DialogWrapperAction {
      ApplyAction() {
        super(DataGridBundle.message("action.ApplyAction.text"));
      }

      @Override
      protected void doAction(ActionEvent e) {
        myForm.apply(mySettings);
        mySettings.fireChanged();
        updateActions();
      }
    }
  }
}
