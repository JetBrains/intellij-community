package com.intellij.database.settings;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.datagrid.HelpID;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CsvSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private ConfigurableUi<CsvFormatsSettings> ui;

  @Override
  public String getDisplayName() {
    return DataGridBundle.message("configurable.DatabaseSettingsConfigurable.CsvFormats.display.name");
  }

  @Override
  public @NotNull String getId() {
    return "database.data.csv.formats";
  }

  @Override
  public void reset() {
    if (ui != null) {
      ui.reset(CsvSettings.getSettings());
    }
  }

  @Override
  public final @NotNull JComponent createComponent() {
    if (ui == null) {
      ui = new CsvFormatsComponent();
    }
    return ui.getComponent();
  }

  @Override
  public @Nullable Runnable enableSearch(String option) {
    return ui == null ? null : ui.enableSearch(option);
  }

  @Override
  public final boolean isModified() {
    return ui != null && ui.isModified(CsvSettings.getSettings());
  }

  @Override
  public final void apply() throws ConfigurationException {
    if (ui != null) {
      ui.apply(CsvSettings.getSettings());
    }
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return ui != null ? ui.getPreferredFocusedComponent() : null;
  }

  @Override
  public void disposeUIResources() {
    ConfigurableUi<CsvFormatsSettings> ui = this.ui;
    if (ui != null) {
      this.ui = null;
      if (ui instanceof Disposable) {
        Disposer.dispose((Disposable)ui);
      }
    }
  }

  @Override
  public @Nullable String getHelpTopic() {
    return HelpID.DATABASE_CVS_SETTINGS;
  }
}
