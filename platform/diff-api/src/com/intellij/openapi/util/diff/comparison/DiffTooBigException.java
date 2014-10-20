package com.intellij.openapi.util.diff.comparison;

import com.intellij.openapi.progress.ProcessCanceledException;

public class DiffTooBigException extends ProcessCanceledException {
  public static final String MESSAGE = "File is too big and there are too many changes.";

  public DiffTooBigException() {
  }

  public DiffTooBigException(Throwable cause) {
    super(cause);
  }


  @Override
  public String getMessage() {
    return MESSAGE;
  }
}
