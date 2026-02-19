// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BundleAccessor {
  @NotNull
  @Nls
  String message(@NotNull String key, Object... params);

  @Nullable
  @Nls
  String messageOrNull(@NotNull String key, Object... params);
}
