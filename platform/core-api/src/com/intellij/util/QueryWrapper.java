// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

@Experimental
@FunctionalInterface
public interface QueryWrapper<Result> {
  boolean wrapExecution(@NotNull Processor<? super @NotNull Processor<? super Result>> executor,
                        @NotNull Processor<? super Result> consumer);
}
