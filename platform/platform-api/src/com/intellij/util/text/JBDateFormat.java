// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

/** @deprecated use methods of {@link DateFormatUtil} */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public final class JBDateFormat {
  private static final JBDateTimeFormatter DEFAULT_FORMATTER = new DefaultJBDateTimeFormatter();

  public static JBDateTimeFormatter getFormatter() {
    return DEFAULT_FORMATTER;
  }
}
