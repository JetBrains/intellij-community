package com.intellij.database.run.ui.grid.editors;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.ParsePosition;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

public class EraDateAndTimeFormatter implements Formatter {
  private final DateAndTimeFormatter<?, ?> myRegularFormatter;
  private final DateAndTimeFormatter<?, ?> myEraFormatter;

  public EraDateAndTimeFormatter(@NotNull DateAndTimeFormatter<?, ?> regularFormatter, @NotNull DateAndTimeFormatter<?, ?> eraFormatter) {
    myRegularFormatter = regularFormatter;
    myEraFormatter = eraFormatter;
  }

  @Override
  public Object parse(@NotNull String value) throws ParseException {
    return myEraFormatter.parse(value);
  }

  @Override
  public Object parse(@NotNull String value, @NotNull ParsePosition position) {
    return myEraFormatter.parse(value, position);
  }

  @Override
  public String format(@NotNull Object value) {
    return getFormatter(value).format(value);
  }

  @Override
  public String toString() {
    return myRegularFormatter.toString();
  }

  private Formatter getFormatter(@NotNull Object value) {
    TemporalAccessor accessor = myRegularFormatter.getTemporalAccessor(value);
    int era =  accessor.isSupported(ChronoField.ERA) ? accessor.get(ChronoField.ERA) : 1;
    return era == 0 ? myEraFormatter : myRegularFormatter;
  }
}
