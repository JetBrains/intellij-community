package com.intellij.database.run.ui.grid.editors;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.ParsePosition;

public interface Formatter {
  Object parse(@NotNull String value) throws ParseException;
  Object parse(@NotNull String value, ParsePosition position);
  String format(Object value);

  class Wrapper implements Formatter {
    private final Formatter myDelegate;

    public Wrapper(@NotNull Formatter delegate) {
      myDelegate = delegate;
    }

    @Override
    public Object parse(@NotNull String value) throws ParseException {
      return myDelegate.parse(value);
    }

    @Override
    public Object parse(@NotNull String value, ParsePosition position) {
      return myDelegate.parse(value, position);
    }

    @Override
    public String format(Object value) {
      return myDelegate.format(value);
    }
  }
}
