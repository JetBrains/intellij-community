/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger;

public class DebugException extends RuntimeException {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public DebugException() {
    super("DebugException");
  }
}
