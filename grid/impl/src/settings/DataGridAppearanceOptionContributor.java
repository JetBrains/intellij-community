package com.intellij.database.settings;

import com.intellij.database.DataGridBundle;
import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import org.jetbrains.annotations.NotNull;

public class DataGridAppearanceOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    processor.addOptions(DataGridBundle.message("configurable.DatabaseSettingsConfigurable.DataViews.additional.search.text"),
                         null,
                         null,
                         DataGridAppearanceConfigurable.ID,
                         null,
                         false);
  }
}