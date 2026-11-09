// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalQueries;
import java.util.Date;

@ApiStatus.Internal
public final class DateFormatAdapter extends DateFormat {
  private final DateTimeFormatter myFormatter;

  public DateFormatAdapter(@NotNull DateTimeFormatter formatter) { myFormatter = formatter; }

  @Override
  public StringBuffer format(Date date, StringBuffer out, FieldPosition pos) {
    return out.append(myFormatter.format(date.toInstant().atZone(ZoneId.systemDefault())));
  }

  @Override
  public Date parse(String text, ParsePosition pos) {
    try {
      var parsed = myFormatter.parse(text);
      var time = parsed.query(TemporalQueries.localTime());
      var value = LocalDateTime.of(LocalDate.from(parsed), time == null ? LocalTime.MIDNIGHT : time);
      pos.setIndex(text.length());
      return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
    catch (DateTimeParseException e) {
      pos.setErrorIndex(e.getErrorIndex());
      return null;
    }
  }
}
