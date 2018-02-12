/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.diff.impl;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface DiffToolSubstitutor {
  ExtensionPointName<DiffToolSubstitutor> EP_NAME =
    ExtensionPointName.create("com.intellij.diff.impl.DiffToolSubstitutor");

  @Nullable
  DiffTool getReplacement(@NotNull DiffTool tool, @NotNull DiffContext context, @NotNull DiffRequest request);
}
