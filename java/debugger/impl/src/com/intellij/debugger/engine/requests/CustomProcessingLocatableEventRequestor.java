// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.SuspendContextImpl;
import org.jetbrains.annotations.NotNull;

public interface CustomProcessingLocatableEventRequestor extends LocatableEventRequestor {
  /**
   * @return `false` iff standard resume procedure is needed after this call.
   * So, `true` means custom processing was done and standard resume should not be used. */
  default boolean customVoteSuspend(@NotNull SuspendContextImpl suspendContext) {
    return false;
  }
}
