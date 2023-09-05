// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.containers.RecentStringInterner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
final class StubStringInterner {
  private final @NotNull RecentStringInterner myStringInterner;

  static StubStringInterner getInstance() {
    return ApplicationManager.getApplication().getService(StubStringInterner.class);
  }

  StubStringInterner() {
    myStringInterner = new RecentStringInterner(ApplicationManager.getApplication());
  }

  @Nullable
  @Contract("null -> null")
  String intern(@Nullable String str) {
    if (str == null) return null;
    return myStringInterner.get(str);
  }
}
