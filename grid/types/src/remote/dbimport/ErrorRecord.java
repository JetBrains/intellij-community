package com.intellij.database.remote.dbimport;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public abstract class ErrorRecord implements Comparable<ErrorRecord>, Serializable {
  private final long myLineNumber;

  protected ErrorRecord(long number) {
    myLineNumber = number;
  }

  public abstract @NotNull String getMessage();

  protected long getLineNumber() {
    return myLineNumber;
  }

  @Override
  public int compareTo(@NotNull ErrorRecord o) {
    return Long.compare(getLineNumber(), o.getLineNumber());
  }

  public static class ExceptionRecord extends ErrorRecord {
    private final Exception myException;

    public ExceptionRecord(@NotNull Exception exception, long number) {
      super(number);
      myException = exception;
    }

    @Override
    public @NotNull String getMessage() {
      return getLineNumber() + ": " + getErrorText();
    }

    protected @NotNull String getErrorText() {
      return StringUtilRt.convertLineSeparators(myException.getMessage(), " ");
    }
  }
}
