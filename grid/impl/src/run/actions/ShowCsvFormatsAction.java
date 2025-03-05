package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.csv.ui.preview.TextCsvFormatPreview;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.HelpID;
import com.intellij.database.extractors.DataExtractorFactory;
import com.intellij.database.extractors.DataExtractorProperties;
import com.intellij.database.extractors.FormatExtractorFactory;
import com.intellij.database.settings.CsvFormatsComponent;
import com.intellij.database.settings.CsvSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class ShowCsvFormatsAction extends DumbAwareAction {
  private static final String ID = "Console.TableResult.Copy.Csv.Settings";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Pair<CsvFormatsComponent, Disposable> pair = getFormatsEditorAndDisposable(e);
    CsvFormatsComponent component = pair.first;
    Disposable disposable = pair.second;

    DialogBuilder builder = new DialogBuilder(getEventProject(e))
      .title(DataGridBundle.message("dialog.title.csv.formats"))
      .dimensionKey(getClass().getName())
      .centerPanel(component.getComponent());
    builder.setHelpId(HelpID.DATA_EXTRACTORS);
    builder.addDisposable(disposable);

    CsvFormatsSettings settings = CsvSettings.getSettings();

    String curName = getSelectedFormatName(e);
    UiNotifyConnector.Once.installOn(component.getComponent(), new Activatable() {
      @Override
      public void showNotify() {
        // update table in modality state of dialog to ensure that invokeLater is executed
        component.reset(settings, curName);
      }
    });

    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      component.apply(settings);
      settings.fireChanged();
    }
  }

  @Nullable
  String getSelectedFormatName(@NotNull AnActionEvent e) {
    return null;
  }

  public static @Nullable AnAction getInstance() {
    return ActionManager.getInstance().getAction(ID);
  }

  private static @NotNull Pair<CsvFormatsComponent, Disposable> getFormatsEditorAndDisposable(AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null || grid.isEmpty()) {
      CsvFormatsComponent c = new CsvFormatsComponent();
      return Pair.create(c, c);
    }

    Disposable disposable = Disposer.newDisposable();
    boolean selectionOnly = grid.getSelectionModel().getSelectedRowCount() > 1 || grid.getSelectionModel().getSelectedColumnCount() > 1;
    CsvFormatsComponent c = new CsvFormatsComponent(new TextCsvFormatPreview(grid, disposable, selectionOnly));
    Disposer.register(disposable, c);
    return Pair.create(c, disposable);
  }

  public static class ForImport extends ShowCsvFormatsAction {
    public ForImport() {
      ActionUtil.copyFrom(this, ID);
    }

    @Override
    @Nullable
    String getSelectedFormatName(@NotNull AnActionEvent e) {
      CsvFormat currentFormat = ChoosePasteFormatAction.PasteType.get().getFormat();
      return currentFormat == null ? null : currentFormat.name;
    }
  }

  public static class ForExport extends ShowCsvFormatsAction {
    public ForExport() {
      ActionUtil.copyFrom(this, ID);
    }

    @Override
    @Nullable
    String getSelectedFormatName(@NotNull AnActionEvent e) {
      DataExtractorFactory factory = DataExtractorProperties.getCurrentExtractorFactory(e.getProject(), GridUtil::suggestPlugin,
                                                                                        CsvSettings.getSettings());
      return factory instanceof FormatExtractorFactory ? factory.getName() : null;
    }
  }
}
