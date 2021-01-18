// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.RecentStringInterner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
final class StubStringInterner {
  @NotNull
  private final RecentStringInterner myStringInterner;

  static StubStringInterner getInstance() {
    return ServiceManager.getService(StubStringInterner.class);
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
