// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class SnapshotInputMappingException extends RuntimeException {
  public SnapshotInputMappingException(@NotNull Throwable cause) {
    super(cause);
  }
}
