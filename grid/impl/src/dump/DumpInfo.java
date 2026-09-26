package com.intellij.database.dump;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DumpInfo {
  private final String myProducerName;
  private final @NlsContexts.NotificationContent String myErrorSummary;
  private final String mySourceName;
  private final @NlsContexts.NotificationTitle String myTitle;
  private final long myRowCount;
  private final int mySourcesCount;

  public DumpInfo(@NlsContexts.NotificationTitle @NotNull String title,
           @NlsContexts.NotificationContent
           @NotNull String errorSummary,
           @Nullable String sourceName,
           @Nullable String producerName,
           long rowCount,
           int sourcesCount) {
    mySourceName = sourceName;
    myTitle = title;
    myRowCount = rowCount;
    myProducerName = producerName;
    myErrorSummary = errorSummary;
    mySourcesCount = sourcesCount;
  }

  public long getRowCount() {
    return myRowCount;
  }

  public @NlsContexts.NotificationContent @NotNull String getErrorSummary() {
    return myErrorSummary;
  }

  public @Nullable String getProducerName() {
    return myProducerName;
  }

  public @Nullable String getSourceName() {
    return mySourceName;
  }

  public int getSourcesCount() {
    return mySourcesCount;
  }

  public @NlsContexts.NotificationTitle @NotNull String getTitle() {
    return myTitle;
  }
}
