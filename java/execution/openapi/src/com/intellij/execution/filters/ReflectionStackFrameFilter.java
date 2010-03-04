package com.intellij.execution.filters;

/**
 * @author peter
 */
public class ReflectionStackFrameFilter extends StackFrameFilter {
  public boolean isInternalFrame(String className, String methodName) {
    return className.startsWith("sun.reflect.");
  }
}
