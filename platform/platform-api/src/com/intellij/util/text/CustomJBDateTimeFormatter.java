// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public class CustomJBDateTimeFormatter extends JBDateTimeFormatter {
  @NotNull private final String myFormatterID;

  public CustomJBDateTimeFormatter(@NotNull String formatterID) {
    myFormatterID = formatterID;
  }

  protected DateFormat getFormat() {
    return DateTimeFormatManager.getInstance().getDateFormat(myFormatterID);
  }

  @Override
  protected boolean isPrettyFormattingSupported() {
    return false;
  }

  @NotNull
  @Override
  public String formatTime(long time) {
    return getFormat().format(new Date(time));
  }

  @NotNull
  @Override
  public String formatTimeWithSeconds(long time) {
    return formatTime(time);
  }

  @NotNull
  @Override
  public String formatDate(long time) {
    return formatTime(time);
  }

  @NotNull
  @Override
  public String formatPrettyDateTime(long time) {
    return formatTime(time);
  }

  @NotNull
  @Override
  public String formatPrettyDate(long time) {
    return formatTime(time);
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
    return formatTime(date);
  }

  @NotNull
  @Override
  public String formatPrettyDate(@NotNull Date date) {
    return formatTime(date);
  }
}
