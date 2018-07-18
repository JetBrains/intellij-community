package org.jetbrains.jetCheck;

/**
 * @author peter
 */
class CannotRestoreValue extends RuntimeException {
  CannotRestoreValue() {
  }

  CannotRestoreValue(String message) {
    super(message);
  }
}
