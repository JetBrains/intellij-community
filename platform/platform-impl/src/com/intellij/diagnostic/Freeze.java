// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginCauseException;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class Freeze extends Throwable implements PluginCauseException {
  private final @Nullable PluginId problematicPluginId;

  Freeze(@Nullable PluginId id, @NotNull List<StackTraceElement> stacktraceCommonPart) {
    problematicPluginId = id;
    setStackTrace(stacktraceCommonPart.toArray(new StackTraceElement[0]));
  }

  @Override
  @Nullable
  public PluginId getProblematicPluginId() {
    return problematicPluginId;
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
