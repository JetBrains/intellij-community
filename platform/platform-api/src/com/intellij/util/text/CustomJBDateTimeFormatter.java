// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public class CustomJBDateTimeFormatter extends JBDateTimeFormatter {
  private final @NotNull SyncDateFormat myDateFormat;
  private final @NotNull SyncDateFormat myDateTimeFormat;
  private final @NotNull SyncDateFormat myDateTimeSecondsFormat;

  public CustomJBDateTimeFormatter(@NotNull String pattern, boolean use24hour) {
    myDateFormat = new SyncDateFormat(new SimpleDateFormat(pattern));
    myDateTimeFormat = new SyncDateFormat(new SimpleDateFormat(pattern + ", " + (use24hour ? "HH:mm" : "h:mm a")));
    myDateTimeSecondsFormat = new SyncDateFormat(new SimpleDateFormat(pattern + ", " + (use24hour ? "HH:mm:ss" : "h:mm:ss a")));
  }

  private @NotNull SyncDateFormat getFormat() {
    return myDateFormat;
  }

  private @NotNull SyncDateFormat getDateTimeFormat() {
    return myDateTimeFormat;
  }

  private @NotNull SyncDateFormat getDateTimeSecondsFormat() {
    return myDateTimeSecondsFormat;
  }

  @Override
  protected boolean isPrettyFormattingSupported() {
    return false;
  }

  @Override
  public @NotNull String formatTime(long time) {
    return getDateTimeFormat().format(new Date(time));
  }

  @Override
  public @NotNull String formatTimeWithSeconds(long time) {
    return getDateTimeSecondsFormat().format(time);
  }

  @Override
  public @NotNull String formatDate(long time) {
    return getFormat().format(time);
  }

  @Override
  public @NotNull String formatPrettyDateTime(long time) {
    if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed() && DateFormatUtil.isPrettyFormattingPossible(time)) {
      return DateFormatUtil.formatPrettyDateTime(time);
    }

    return formatTime(time);
  }

  @Override
  public @NotNull String formatPrettyDate(long time) {
    if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed() && DateFormatUtil.isPrettyFormattingPossible(time)) {
      return DateFormatUtil.formatPrettyDate(time);
    }
    return formatDate(time);
  }

  @Override
  public @NotNull String formatDateTime(Date date) {
    return formatTime(date);
  }

  @Override
  public @NotNull String formatDateTime(long time) {
    return formatTime(time);
  }

  @Override
  public @NotNull String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  @Override
  public @NotNull String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }
}
