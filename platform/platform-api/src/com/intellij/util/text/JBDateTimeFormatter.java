// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public class JBDateTimeFormatter {
  private final String myFormatterID;

  public JBDateTimeFormatter() {
    this(null);
  }

  public JBDateTimeFormatter(String formatterID) {
    myFormatterID = formatterID;
  }

  @Nullable
  private DateFormat getDateFormat() {
    DateFormat dateFormat = null;
    if (myFormatterID != null) {
      dateFormat = DateTimeFormatManager.getInstance().getDateFormat(myFormatterID);
    }
    return dateFormat;
  }

  private boolean isPrettyFormattingSupported() {
    return DateTimeFormatManager.getInstance().isPrettyFormattingAllowed(myFormatterID);
  }

  @NotNull
  public String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  @NotNull
  public String formatTime(long time) {
    return DateFormatUtil.formatTime(time);
  }

  @NotNull
  public static String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  @NotNull
  public static String formatTimeWithSeconds(long time) {
    return DateFormatUtil.formatTimeWithSeconds(time);
  }

  @NotNull
  public static String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  @NotNull
  public static String formatDate(long time) {
    return DateFormatUtil.formatDate(time);
  }

  @NotNull
  public String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  @NotNull
  public String formatDateTime(long time) {
    return DateFormatUtil.formatDateTime(time);
  }


  @NotNull
  public String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  @NotNull
  public String formatPrettyDateTime(long time) {
    if (isPrettyFormattingSupported()) {
      return DateFormatUtil.formatPrettyDateTime(time);
    }
    return formatDateTime(time);
  }
}
