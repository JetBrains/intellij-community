// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author egor
 */
public interface AsyncStackTraceProvider {
  ExtensionPointName<AsyncStackTraceProvider> EP = ExtensionPointName.create("com.intellij.debugger.asyncStackTraceProvider");

  @Nullable
  List<StackFrameItem> getAsyncStackTrace(@NotNull JavaStackFrame stackFrame, @NotNull SuspendContextImpl suspendContext);
}
