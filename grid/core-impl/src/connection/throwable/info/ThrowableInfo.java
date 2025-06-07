package com.intellij.database.connection.throwable.info;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ThrowableInfo {
  @Nullable
  Throwable getOriginalThrowable();

  @NotNull
  @NlsContexts.NotificationContent
  @NlsContexts.DetailedDescription
  String getMessage();

  default @NotNull @NlsContexts.NotificationContent @NlsContexts.DetailedDescription String getLogMessage() {
    return getMessage();
  }
}
