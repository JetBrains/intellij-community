// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public enum DumbModeAccessType {
  RELIABLE_DATA_ONLY,
  RAW_INDEX_DATA_ACCEPTABLE;

  public void ignoreDumbMode(@NotNull Runnable command) {
    FileBasedIndex.getInstance().ignoreDumbMode(this, command);
  }

  public <T, E extends Throwable> T ignoreDumbMode(@NotNull ThrowableComputable<T, E> computable) throws E {
    return FileBasedIndex.getInstance().ignoreDumbMode(this, computable);
  }
}
