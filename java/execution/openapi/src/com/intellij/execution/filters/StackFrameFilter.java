package com.intellij.execution.filters;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author peter
 */
public abstract class StackFrameFilter {
  public static final ExtensionPointName<StackFrameFilter> EP_NAME = ExtensionPointName.create("com.intellij.stackFrameFilter");

  public abstract boolean isAuxiliaryFrame(String className, String methodName);

}
