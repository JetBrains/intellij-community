package org.jetbrains.jps.incremental;

/**
 * @author nik
 */
public abstract class Builder {
  public abstract String getName();

  public abstract String getDescription();

  public static enum ExitCode {
    OK, ABORT, ADDITIONAL_PASS_REQUIRED
  }
}
