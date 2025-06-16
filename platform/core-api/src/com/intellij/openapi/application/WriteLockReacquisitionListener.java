// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;

import java.util.EventListener;

@ApiStatus.Internal
public interface WriteLockReacquisitionListener extends EventListener {
  void beforeWriteLockReacquired();
}
