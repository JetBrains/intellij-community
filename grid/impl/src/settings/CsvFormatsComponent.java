package com.intellij.database.settings;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.csv.ui.CsvFormatsSettingsUI;
import com.intellij.database.csv.ui.CsvFormatsUI;
import com.intellij.database.csv.ui.FormatsListAndPreviewPanel;
import com.intellij.database.csv.ui.preview.CsvFormatPreview;
import com.intellij.database.csv.ui.preview.TableAndTextCsvFormatPreview;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CsvFormatsComponent implements ConfigurableUi<CsvFormatsSettings>, Disposable {
  private final JPanel myPanel;
  private final CsvFormatsUI myFormatsEditor;

  public CsvFormatsComponent() {
    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    myFormatsEditor = new CsvFormatsSettingsUI(this);
    myPanel = new FormatsListAndPreviewPanel(myFormatsEditor, new TableAndTextCsvFormatPreview(project, this));
  }

  public CsvFormatsComponent(@NotNull CsvFormatPreview preview) {
    myFormatsEditor = new CsvFormatsSettingsUI(this);
    myPanel = new FormatsListAndPreviewPanel(myFormatsEditor, preview);
  }

  @Override
  public void reset(@NotNull CsvFormatsSettings settings) {
    reset(settings, null);
  }

  public void reset(@NotNull CsvFormatsSettings settings, @Nullable String name) {
    List<CsvFormat> formats = settings.getCsvFormats();
    reset(formats, name);
  }

  public void reset(@NotNull List<CsvFormat> formats, @Nullable String name) {
    myFormatsEditor.reset(formats, name);
  }

  public @Nullable CsvFormat select(@Nullable CsvFormat format) {
    return myFormatsEditor.select(format);
  }

  @Override
  public boolean isModified(@NotNull CsvFormatsSettings settings) {
    List<CsvFormat> formats = settings.getCsvFormats();
    List<CsvFormat> formatsInEditor = getFormats();
    return !formats.equals(formatsInEditor);
  }

  @Override
  public void apply(@NotNull CsvFormatsSettings settings) {
    settings.setCsvFormats(getFormats());
    settings.fireChanged();
  }

  public @NotNull List<CsvFormat> getFormats() {
    return myFormatsEditor.getFormats();
  }

  public @Nullable CsvFormat getSelectedFormat() {
    return myFormatsEditor.getSelectedFormat();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }
}
