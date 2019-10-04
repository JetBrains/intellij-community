// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public String formatTime(long time) {
    return DateFormatUtil.formatTime(time);
  }

  @Override
  @NotNull
  public String formatTimeWithSeconds(long time) {
    return DateFormatUtil.formatTimeWithSeconds(time);
  }

  @Override
  @NotNull
  public String formatDate(long time) {
    return DateFormatUtil.formatDate(time);
  }

  @Override
  @NotNull
  public String formatPrettyDateTime(long time) {
    if (isPrettyFormattingSupported()) {
      return DateFormatUtil.formatPrettyDateTime(time);
    }
    return formatDateTime(time);
  }

  @Override
  @NotNull
  public String formatPrettyDate(long time) {
    if (isPrettyFormattingSupported()) {
      return DateFormatUtil.formatPrettyDate(time);
    }
    return formatDate(time);
  }
}
