package com.intellij.platform.templates;

/**
 * @author Sergey Simonchik
 */
class GeneratorException extends Exception {
  public GeneratorException(String message) {
    super(message);
  }

  public GeneratorException(String message, Throwable cause) {
    super(message, cause);
  }
}
