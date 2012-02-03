package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/20/11
 */
public class ProjectBuildException extends Exception{
  private final boolean myIsError;

  public ProjectBuildException() {
    myIsError = false;
  }

  public ProjectBuildException(String message) {
    this(message, false);
  }

  public ProjectBuildException(String message, boolean isError) {
    super(message);
    myIsError = isError;
  }

  public ProjectBuildException(String message, Throwable cause) {
    super(message, cause);
    myIsError = true;
  }

  public ProjectBuildException(Throwable cause) {
    super(cause);
    myIsError = true;
  }

  public boolean isError() {
    return myIsError;
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
