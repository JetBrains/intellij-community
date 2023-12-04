// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** @deprecated use {@link java.time.format.DateTimeFormatter} */
@Deprecated(forRemoval = true)
@SuppressWarnings("DeprecatedIsStillUsed")
public final class SyncDateFormat {
  private final DateFormat myDelegate;

  public SyncDateFormat(@NotNull DateFormat delegate) {
    myDelegate = delegate;
  }

  public synchronized Date parse(@NotNull String s) throws ParseException {
    return myDelegate.parse(s);
  }

  public synchronized @NlsSafe String format(@NotNull Date date) {
    return myDelegate.format(date);
  }

  public synchronized @NlsSafe String format(long time) {
    return myDelegate.format(time);
  }

  public synchronized DateFormat getDelegate() {
    return (DateFormat)myDelegate.clone();
  }

  public synchronized String toPattern() {
    if (myDelegate instanceof SimpleDateFormat) {
      return ((SimpleDateFormat)myDelegate).toPattern();
    }
    throw new UnsupportedOperationException("Delegate must be of SimpleDateFormat type");
  }
}
