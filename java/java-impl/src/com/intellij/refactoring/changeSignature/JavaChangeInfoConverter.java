// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface JavaChangeInfoConverter {
  @Nullable JavaChangeInfo toJavaChangeInfo(@NotNull ChangeInfo changeInfo, UsageInfo usage);
  @Nullable ChangeInfo fromJavaChangeInfo(@NotNull JavaChangeInfo changeInfo, UsageInfo usage, Boolean beforeMethodChanged);
}
