// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TestDialog {
  TestDialog DEFAULT = message -> {
    throw new RuntimeException(message);
  };
  TestDialog YES = _ -> Messages.YES;
  TestDialog OK = _ -> Messages.OK;
  TestDialog NO = _ -> Messages.NO;

  int show(@NotNull String message);

  default int show(@NotNull String message, @Nullable DoNotAskOption doNotAskOption) {
    return show(message);
  }
}
