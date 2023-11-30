// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBDateTimeFormatter {
  protected abstract boolean isPrettyFormattingSupported();

  public @NotNull String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  public abstract @NotNull String formatTime(long time);

  public @NotNull String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  public abstract @NotNull String formatTimeWithSeconds(long time);

  public @NotNull @NlsSafe String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  public abstract @NotNull String formatDate(long time);

  public @NotNull String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  public @NotNull @NlsSafe String formatDateTime(long time) {
    return DateFormatUtil.formatDateTime(time);
  }

  public @NotNull @NlsSafe String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  public abstract @NotNull String formatPrettyDateTime(long time);

  public @NotNull String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  public abstract @NotNull String formatPrettyDate(long time);
}
