package com.intellij.database.csv.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormats;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class CsvFormatsSettingsUI extends CsvFormatsUI {

  public CsvFormatsSettingsUI(@NotNull Disposable parent) {
    this(true, parent, CsvFormatUISettings.DEFAULT);
  }

  public CsvFormatsSettingsUI(boolean allowNameEditing, @NotNull Disposable parent, @NotNull CsvFormatUISettings settings) {
    super(allowNameEditing, settings);
    Disposer.register(parent, this);
  }

  @Override
  protected @NotNull ToolbarDecorator createFormatListDecorator() {
    return ToolbarDecorator.createDecorator(getFormatsListComponent().getComponent())
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .setAddAction(button -> addNewAndEditName(CsvFormats.TSV_FORMAT.getValue()))

      .setEditAction(button -> editName(getNotNullSelectedFormat()))
      .setEditActionUpdater(e -> getFormatsListComponent().getSelected() != null)

      .addExtraAction(new CopyFormatAction())

      .setAddActionName(DataGridBundle.message("csv.format.settings.add"))
      .setRemoveActionName(DataGridBundle.message("csv.format.settings.remove"))

      .setButtonComparator(DataGridBundle.message("csv.format.settings.add"),
                           DataGridBundle.message("csv.format.settings.remove"),
                           DataGridBundle.message("csv.format.settings.copy"));
  }

  private @NotNull CsvFormat getNotNullSelectedFormat() {
    return Objects.requireNonNull(getFormatsListComponent().getSelected());
  }

  private void addNewAndEditName(@NotNull CsvFormat templateFormat) {
    editName(getFormatsListComponent().newFormat(templateFormat));
  }

  private void editName(@NotNull CsvFormat format) {
    getFormatsListComponent().editFormatName(format, null);
  }


  private class CopyFormatAction extends DumbAwareAction {
    CopyFormatAction() {
      super(DataGridBundle.message("csv.format.settings.copy"), null, PlatformIcons.COPY_ICON);
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_DUPLICATE));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedFormat() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addNewAndEditName(getNotNullSelectedFormat());
    }
  }
}
