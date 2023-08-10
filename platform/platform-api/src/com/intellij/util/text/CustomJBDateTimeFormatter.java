// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public class CustomJBDateTimeFormatter extends JBDateTimeFormatter {
  @NotNull private final SyncDateFormat myDateFormat;
  @NotNull private final SyncDateFormat myDateTimeFormat;
  @NotNull private final SyncDateFormat myDateTimeSecondsFormat;

  public CustomJBDateTimeFormatter(@NotNull String pattern, boolean use24hour) {
    myDateFormat = new SyncDateFormat(new SimpleDateFormat(pattern));
    myDateTimeFormat = new SyncDateFormat(new SimpleDateFormat(pattern + ", " + (use24hour ? "HH:mm" : "h:mm a")));
    myDateTimeSecondsFormat = new SyncDateFormat(new SimpleDateFormat(pattern + ", " + (use24hour ? "HH:mm:ss" : "h:mm:ss a")));
  }

  @NotNull
  private SyncDateFormat getFormat() {
    return myDateFormat;
  }

  @NotNull
  private SyncDateFormat getDateTimeFormat() {
    return myDateTimeFormat;
  }

  @NotNull
  private SyncDateFormat getDateTimeSecondsFormat() {
    return myDateTimeSecondsFormat;
  }

  @Override
  protected boolean isPrettyFormattingSupported() {
    return false;
  }

  @NotNull
  @Override
  public String formatTime(long time) {
    return getDateTimeFormat().format(new Date(time));
  }

  @NotNull
  @Override
  public String formatTimeWithSeconds(long time) {
    return getDateTimeSecondsFormat().format(time);
  }

  @NotNull
  @Override
  public String formatDate(long time) {
    return getFormat().format(time);
  }

  @NotNull
  @Override
  public String formatPrettyDateTime(long time) {
    if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed() && DateFormatUtil.isPrettyFormattingPossible(time)) {
      return DateFormatUtil.formatPrettyDateTime(time);
    }

    return formatTime(time);
  }

  @NotNull
  @Override
  public String formatPrettyDate(long time) {
    if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed() && DateFormatUtil.isPrettyFormattingPossible(time)) {
      return DateFormatUtil.formatPrettyDate(time);
    }
    return formatDate(time);
  }

  @NotNull
  @Override
  public String formatDateTime(Date date) {
    return formatTime(date);
  }

  @NotNull
  @Override
  public String formatDateTime(long time) {
    return formatTime(time);
  }

  @NotNull
  @Override
  public String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  @NotNull
  @Override
  public String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }
}
