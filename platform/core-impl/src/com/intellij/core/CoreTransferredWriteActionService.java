// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.util.concurrency.TransferredWriteActionService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The write action transfer of an environment without a real write lock and without an event dispatch thread. It runs the action on
 * the current thread. The IDE implementation moves the write access to the event dispatch thread or to a background thread.
 *
 * @see CoreApplicationEnvironment#createTransferredWriteActionService
 */
@ApiStatus.Internal
public class CoreTransferredWriteActionService implements TransferredWriteActionService {
  @Override
  public void runOnEdtWithTransferredWriteActionAndWait(@NotNull Runnable action) {
    action.run();
  }

  @Override
  public void runOnBackgroundThreadWithTransferredWriteActionAndWait(@NotNull Runnable action) {
    action.run();
  }
}
