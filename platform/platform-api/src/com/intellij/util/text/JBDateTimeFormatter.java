// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/** @deprecated use methods of {@link DateFormatUtil} */
@Deprecated(forRemoval = true)
@SuppressWarnings("unused")
public abstract class JBDateTimeFormatter {
  public @NotNull @NlsSafe String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  public @NotNull @NlsSafe String formatTime(long time) {
    return DateFormatUtil.formatTime(time);
  }

  public @NotNull @NlsSafe String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  public @NotNull @NlsSafe String formatTimeWithSeconds(long time) {
    return DateFormatUtil.formatTimeWithSeconds(time);
  }

  public @NotNull @NlsSafe String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  public @NotNull @NlsSafe String formatDate(long time) {
    return DateFormatUtil.formatDate(time);
  }

  public @NotNull @NlsSafe String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  public @NotNull @NlsSafe String formatDateTime(long time) {
    return DateFormatUtil.formatDateTime(time);
  }

  public @NotNull @NlsSafe String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  public @NotNull @NlsSafe String formatPrettyDateTime(long time) {
    return DateFormatUtil.formatPrettyDateTime(time);
  }

  public @NotNull @NlsSafe String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  public @NotNull @NlsSafe String formatPrettyDate(long time) {
    return DateFormatUtil.formatPrettyDate(time);
  }
}
