package com.intellij.database.run.ui.grid.editors;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.ParsePosition;
import java.util.List;

public class CompositeFormatter extends FormatterImpl {
  private final Formatter[] myFormatters;
  private final Formatter myBaseFormatter;
  private final @NotNull @Nls String myErrorMessage;

  public CompositeFormatter(@NotNull @Nls String errorMessage, @NotNull List<Formatter> formatters) {
    this(errorMessage, ContainerUtil.toArray(formatters, Formatter[]::new));
  }

  public CompositeFormatter(@NotNull @Nls String errorMessage, Formatter @NotNull ... formatters) {
    if (formatters.length == 0) throw new IllegalArgumentException("Formatters must contains at least one formatter");
    myBaseFormatter = formatters[0];
    myFormatters = formatters;
    myErrorMessage = errorMessage;
  }

  @Override
  public Object parse(@NotNull String value, @NotNull ParsePosition position) {
    ParsePosition internal = new ParsePosition(0);
    for (Formatter formatter : myFormatters) {
      internal.setIndex(0);
      internal.setErrorIndex(-1);
      Object result = formatter.parse(value, internal);
      if (internal.getErrorIndex() == -1 && internal.getIndex() == value.length()) {
        position.setIndex(internal.getIndex());
        return result;
      }
    }
    position.setErrorIndex(internal.getErrorIndex());
    return null;
  }

  @Override
  protected @Nls @NotNull String getErrorMessage() {
    return myErrorMessage;
  }

  @Override
  public String format(@NotNull Object value) {
    return myBaseFormatter.format(value);
  }

  @Override
  public String toString() {
    return myBaseFormatter.toString();
  }
}
