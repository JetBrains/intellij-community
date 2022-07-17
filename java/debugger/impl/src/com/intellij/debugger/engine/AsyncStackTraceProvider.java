// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allows to replace a part of the debugger call stack with the "async" part:
 * every frame is asked for async stack trace via {@link #getAsyncStackTrace(JavaStackFrame, SuspendContextImpl)}
 * and if it returns something - it replaces the rest of the stack.
 */
public interface AsyncStackTraceProvider {
  ExtensionPointName<AsyncStackTraceProvider> EP = ExtensionPointName.create("com.intellij.debugger.asyncStackTraceProvider");

  @Nullable
  List<StackFrameItem> getAsyncStackTrace(@NotNull JavaStackFrame stackFrame, @NotNull SuspendContextImpl suspendContext);
}
