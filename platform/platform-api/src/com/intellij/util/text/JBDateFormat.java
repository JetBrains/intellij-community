// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

/**
 * @author Konstantin Bulenkov
 */
public final class JBDateFormat {
  private static final JBDateTimeFormatter DEFAULT_FORMATTER = new DefaultJBDateTimeFormatter();
  static CustomJBDateTimeFormatter CUSTOM_FORMATTER;

  static {
    invalidateCustomFormatter();
  }

  public static JBDateTimeFormatter getFormatter() {
    if (DateTimeFormatManager.getInstance().isOverrideSystemDateFormat()) {
      return CUSTOM_FORMATTER;
    }

    return DEFAULT_FORMATTER;
  }

  public static void invalidateCustomFormatter() {
    DateTimeFormatManager settings = DateTimeFormatManager.getInstance();
    CUSTOM_FORMATTER = new CustomJBDateTimeFormatter(settings.getDateFormatPattern(), settings.isUse24HourTime());
  }
}
