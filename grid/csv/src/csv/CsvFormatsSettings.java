package com.intellij.database.csv;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CsvFormatsSettings {
  Topic<Listener> TOPIC = Topic.create("Csv Formats settings", Listener.class);

  @NotNull
  List<CsvFormat> getCsvFormats();

  void setCsvFormats(@NotNull List<CsvFormat> formats);

  void fireChanged();

  static boolean formatsSimilar(@NotNull CsvFormat format, @Nullable CsvFormat other) {
    return other != null &&
           other.rowNumbers == format.rowNumbers &&
           Comparing.equal(other.headerRecord, format.headerRecord) &&
           Comparing.equal(other.dataRecord, format.dataRecord);
  }

  interface Listener {
    void settingsChanged();
  }
}
