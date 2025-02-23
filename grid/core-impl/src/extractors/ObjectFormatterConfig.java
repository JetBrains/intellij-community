package com.intellij.database.extractors;

import com.intellij.database.settings.DataGridSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ObjectFormatterConfig {

  @NotNull
  ObjectFormatterMode getMode();

  @Nullable
  DataGridSettings getSettings();

  boolean isAllowedShowBigObjects();
}
