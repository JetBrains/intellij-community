package com.intellij.database.run.ui.grid.editors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.ParsePosition;

public abstract class FormatterImpl implements Formatter {
  @Override
  public Object parse(@NotNull String value) throws ParseException {
    ParsePosition position = new ParsePosition(0);
    Object res = parse(value, position);
    int errIdx = position.getErrorIndex();
    if (errIdx == -1 && position.getIndex() != value.length()) {
      errIdx = position.getIndex();
    }
    if (errIdx != -1) {
      throw new ParseException(getErrorMessage(), errIdx);
    }
    return res;
  }

  protected abstract @NotNull @Nls String getErrorMessage();
}
