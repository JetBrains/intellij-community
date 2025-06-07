package com.intellij.database.run.ui.grid.editors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnparsedValue {
  private final String myText;
  private final ParsingError myError;

  public UnparsedValue(@NotNull String text, String n) {
    this(text);
  }

  public UnparsedValue(@NotNull String text) {
    this(text, (ParsingError)null);
  }

  public UnparsedValue(@NotNull String text, @Nullable ParsingError error) {
    myText = text;
    myError = error;
  }

  public @NotNull String getText() {
    return myText;
  }

  public @Nullable ParsingError getError() {
    return myError;
  }

  @Override
  public String toString() {
    return myText;
  }

  public record ParsingError(@NotNull @Nls String message, int offset) {
    public ParsingError(@Nls @NotNull String message) {
      this(message, 0);
    }
  }
}
