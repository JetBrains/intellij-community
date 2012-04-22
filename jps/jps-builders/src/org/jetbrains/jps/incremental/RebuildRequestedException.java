package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/20/11
 */
public class RebuildRequestedException extends ProjectBuildException{

  public RebuildRequestedException(Throwable cause) {
    super(cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
