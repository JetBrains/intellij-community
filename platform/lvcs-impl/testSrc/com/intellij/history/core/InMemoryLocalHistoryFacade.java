// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.core;

import org.jetbrains.annotations.NotNull;

public final class InMemoryLocalHistoryFacade extends LocalHistoryFacade {
  public InMemoryLocalHistoryFacade() {
  }

  @Override
  protected @NotNull ChangeListStorage createStorage() {
    return new InMemoryChangeListStorage();
  }
}
