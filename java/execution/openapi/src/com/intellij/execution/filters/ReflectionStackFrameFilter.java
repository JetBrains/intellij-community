package com.intellij.execution.filters;

/**
 * @author peter
 */
public class ReflectionStackFrameFilter extends StackFrameFilter {
  public boolean isAuxiliaryFrame(String className, String methodName) {
    if (className.equals("java.lang.reflect.Method") && methodName.equals("invoke")) {
      return true;
    }
    if (className.equals("java.lang.reflect.Constructor") && methodName.equals("newInstance")) {
      return true;
    }

    return className.startsWith("sun.reflect.");
  }
}
