package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/20/11
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
