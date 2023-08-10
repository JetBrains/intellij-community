// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBDateTimeFormatter {
  protected abstract boolean isPrettyFormattingSupported();

  @NotNull
  public String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  @NotNull
  public abstract String formatTime(long time);

  @NotNull
  public String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  @NotNull
  public abstract String formatTimeWithSeconds(long time);

  @NotNull
  public @NlsSafe String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  @NotNull
  public abstract String formatDate(long time);

  @NotNull
  public String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  @NotNull
  public @NlsSafe String formatDateTime(long time) {
    return DateFormatUtil.formatDateTime(time);
  }

  @NotNull
  public @NlsSafe String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  @NotNull
  public abstract String formatPrettyDateTime(long time);

  @NotNull
  public String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  @NotNull
  public abstract String formatPrettyDate(long time);
}
