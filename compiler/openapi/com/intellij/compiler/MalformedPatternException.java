package com.intellij.compiler;

public class MalformedPatternException extends RuntimeException {
  public MalformedPatternException(Throwable cause) {
    super(cause);
  }

  @Override
  public String getLocalizedMessage() {
    return getCause().getLocalizedMessage();
  }
}
