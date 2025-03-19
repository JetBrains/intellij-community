package com.intellij.database.settings;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DataGridAppearanceSettings {
  Topic<Listener> TOPIC = Topic.create("Data Editor and Viewer Settings", Listener.class);

  static DataGridAppearanceSettings getSettings() {
    return DataGridAppearanceSettingsImpl.getSettings();
  }

  boolean getUseGridCustomFont();

  void setUseGridCustomFont(boolean value);

  @Nullable
  String getGridFontFamily();

  void setGridFontFamily(@Nullable String value);

  int getGridFontSize();

  void setGridFontSize(int value);

  float getGridLineSpacing();

  void setGridLineSpacing(float value);

  boolean isStripedTable();

  void setStripedTable(boolean value);

  BooleanMode getBooleanMode();

  void setBooleanMode(@NotNull BooleanMode mode);

  enum BooleanMode {
    TEXT,
    CHECKBOX
  }

  interface Listener {
    void settingsChanged();
  }
}
