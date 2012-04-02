package org.jetbrains.jps.javac;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/2/12
 */
class CompilationCanceledException extends RuntimeException{
  CompilationCanceledException() {
    super("Compilation canceled");
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
