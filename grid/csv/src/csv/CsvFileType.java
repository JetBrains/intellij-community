package com.intellij.database.csv;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class CsvFileType extends LanguageFileType {
  public static final CsvFileType INSTANCE = new CsvFileType();

  private CsvFileType() {
    super(PlainTextLanguage.INSTANCE, true);
  }

  @Override
  public @NotNull String getName() {
    return "CSV/TSV";
  }

  @Override
  public @NotNull String getDescription() {
    return GridCsvBundle.message("filetype.csv.tsv.data.description");
  }

  @Override
  public @NotNull String getDisplayName() {
    return GridCsvBundle.message("filetype.csv.tsv.data.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "csv";
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.TextFileType);
  }
}
