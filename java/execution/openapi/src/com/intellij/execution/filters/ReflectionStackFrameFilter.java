package com.intellij.execution.filters;

/**
 * @author peter
 */
public class ReflectionStackFrameFilter extends StackFrameFilter {
  public boolean isAuxiliaryFrame(String className, String methodName) {
    return className.startsWith("sun.reflect.");
  }
}
