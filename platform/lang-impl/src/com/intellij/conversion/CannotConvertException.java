package com.intellij.conversion;

/**
 * @author nik
 */
public class CannotConvertException extends Exception {
  public CannotConvertException(String message) {
    super(message);
  }

  public CannotConvertException(String message, Throwable cause) {
    super(message, cause);
  }
}
