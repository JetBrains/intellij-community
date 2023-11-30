// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class DefaultJBDateTimeFormatter extends JBDateTimeFormatter {

  @Override
  protected boolean isPrettyFormattingSupported() {
    return DateTimeFormatManager.getInstance().isPrettyFormattingAllowed();
  }

  @Override
  public @NotNull String formatTime(long time) {
    return DateFormatUtil.formatTime(time);
  }

  @Override
  public @NotNull String formatTimeWithSeconds(long time) {
    return DateFormatUtil.formatTimeWithSeconds(time);
  }

  @Override
  public @NotNull String formatDate(long time) {
    return DateFormatUtil.formatDate(time);
  }

  @Override
  public @NotNull String formatPrettyDateTime(long time) {
    if (isPrettyFormattingSupported()) {
      return DateFormatUtil.formatPrettyDateTime(time);
    }
    return formatDateTime(time);
  }

  @Override
  public @NotNull String formatPrettyDate(long time) {
    if (isPrettyFormattingSupported()) {
      return DateFormatUtil.formatPrettyDate(time);
    }
    return formatDate(time);
  }
}
