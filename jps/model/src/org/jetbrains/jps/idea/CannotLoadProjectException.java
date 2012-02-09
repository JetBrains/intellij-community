package org.jetbrains.jps.idea;

/**
 * @author nik
 */
public class CannotLoadProjectException extends RuntimeException {
  public CannotLoadProjectException(String message) {
    super(message);
  }
}
