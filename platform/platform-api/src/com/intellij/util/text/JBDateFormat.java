// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;

/**
 * @author Konstantin Bulenkov
 */
public class JBDateFormat {
  private static final JBDateTimeFormatter DEFAULT_FORMATTER = new DefaultJBDateTimeFormatter();

  public static JBDateTimeFormatter getDefaultFormatter() {
    return DEFAULT_FORMATTER;
  }

  public static JBDateTimeFormatter getFormatter(@NotNull String formatterID) {
    DateFormat format = DateTimeFormatManager.getInstance().getDateFormat(formatterID);
    if (format == null) return DEFAULT_FORMATTER;
    return new CustomJBDateTimeFormatter(formatterID);
  }
}
