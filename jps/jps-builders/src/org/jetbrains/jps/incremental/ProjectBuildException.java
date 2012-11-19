package org.jetbrains.jps.incremental;

/**
 * Use this exception to signal that the build must be stopped
 * If Throwable cause of the stop is provided, the reason is assumed to be an unexpected internal error,
 * so the corresponding error message "internal error" with stacktrace is additionally reported
 *
 * If no Throwable cause is provided, it is assumed that all the errors were reported by the builder previously and the build is just stopped
 * Optional message, if provided, is reported as a progress message.
 */
public class ProjectBuildException extends Exception{
  public ProjectBuildException() {
  }

  public ProjectBuildException(String message) {
    super(message);
  }

  public ProjectBuildException(String message, Throwable cause) {
    super(message, cause);
  }

  public ProjectBuildException(Throwable cause) {
    super(cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
