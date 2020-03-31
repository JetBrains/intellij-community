// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public class CustomJBDateTimeFormatter extends JBDateTimeFormatter {
  @NotNull private final DateFormat myDateFormat;
  @NotNull private final DateFormat myDateTimeFormat;
  @NotNull private final DateFormat myDateTimeSecondsFormat;

  public CustomJBDateTimeFormatter(@NotNull String pattern, boolean use24hour) {
    myDateFormat = new SimpleDateFormat(pattern);
    myDateTimeFormat = new SimpleDateFormat(pattern + ", " + (use24hour ? "HH:mm" : "h:mm a"));
    myDateTimeSecondsFormat = new SimpleDateFormat(pattern + ", " + (use24hour ? "HH:mm:ss" : "h:mm:ss a"));
  }

  @NotNull
  protected DateFormat getFormat() {
    return myDateFormat;
  }

  @NotNull
  protected DateFormat getDateTimeFormat() {
    return myDateTimeFormat;
  }

  @NotNull
  protected DateFormat getDateTimeSecondsFormat() {
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
