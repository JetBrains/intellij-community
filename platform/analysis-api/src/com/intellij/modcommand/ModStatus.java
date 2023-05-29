// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

/**
 * Status of the execution of {@link ModCommand}
 */
public enum ModStatus {
  /**
   * Operation completed successfully
   */
  SUCCESS,
  /**
   * Operation continues execution in background
   */
  DEFERRED,
  /**
   * Operation aborted due to intermittent state change, no changes are introduced,
   * recreating the command and performing the operation again may be helpful
   */
  ABORT,
  /**
   * Operation cancelled by user via UI
   */
  CANCEL
}
