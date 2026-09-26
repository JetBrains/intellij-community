// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

public class CompositeModificationTracker extends SimpleModificationTracker {
  private final @NotNull ModificationTracker myAdditionalTracker;

  public CompositeModificationTracker(@NotNull ModificationTracker tracker) {
    myAdditionalTracker = tracker;
  }

  @Override
  public long getModificationCount() {
    return super.getModificationCount() + myAdditionalTracker.getModificationCount();
  }
}
