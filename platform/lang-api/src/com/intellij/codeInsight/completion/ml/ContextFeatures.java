// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface ContextFeatures extends UserDataHolder {
  @Nullable
  Boolean binaryValue(@NotNull String name);

  @Nullable
  Double floatValue(@NotNull String name);

  @Nullable
  String categoricalValue(@NotNull String name);

  @NotNull
  Map<String, String> asMap();
}
