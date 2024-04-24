// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.SuspendContextImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public interface CustomProcessingLocatableEventRequestor extends LocatableEventRequestor {
  /**
   * @return `false` iff standard resume procedure is needed after this call.
   * So, `true` means custom processing was done and standard resume should not be used.
   * </p>
   * Note that this method is not called in the DebuggerUtils#isAlwaysSuspendThreadBeforeSwitch mode. Instead use [applyAfterContextSwitch].
   */
  boolean customVoteSuspend(@NotNull SuspendContextImpl suspendContext);

  /** This method is used only in the DebuggerUtils#isAlwaysSuspendThreadBeforeSwitch mode. */
  @ApiStatus.Internal
  @Nullable Function<SuspendContextImpl, Boolean> applyAfterContextSwitch();
}
