package com.intellij.openapi.ui;

public class NonEmptyInputValidator implements InputValidator {
  public boolean checkInput(final String inputString) {
    return inputString.length() > 0;
  }

  public boolean canClose(final String inputString) {
    return checkInput(inputString);
  }
}
