// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Allows to support custom breakpoint types in java debugger.
 *
 * @see JavaBreakpointHandler
 */
public interface JavaBreakpointHandlerFactory {
  ExtensionPointName<JavaBreakpointHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.debugger.javaBreakpointHandlerFactory");

  JavaBreakpointHandler createHandler(DebugProcessImpl process);
}
