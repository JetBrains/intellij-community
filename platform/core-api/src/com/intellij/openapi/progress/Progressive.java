// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;

@Obsolete
@FunctionalInterface
public interface Progressive {

  void run(@NotNull ProgressIndicator indicator);

}
