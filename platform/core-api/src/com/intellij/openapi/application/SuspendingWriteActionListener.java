// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;

import java.util.EventListener;

/**
 * Shall be removed in IJPL-149765
 */
@ApiStatus.Obsolete
@ApiStatus.Internal
public interface SuspendingWriteActionListener extends EventListener {
  void beforeWriteLockReacquired();
}
