// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.diagnostic.VMOptions.MemoryKind;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DefaultIdeaErrorLogger {
  public static @Nullable MemoryKind getOOMErrorKind(@NotNull Throwable t) {
    var message = t.getMessage();

    if (t instanceof OutOfMemoryError) {
      if (message != null) {
        if (message.contains("unable to create") && message.contains("native thread")) return null;
        if (message.contains("Metaspace")) return MemoryKind.METASPACE;
        if (message.contains("direct buffer memory")) return MemoryKind.DIRECT_BUFFERS;
      }
      return MemoryKind.HEAP;
    }

    if (t instanceof VirtualMachineError && message != null && message.contains("CodeCache")) {
      return MemoryKind.CODE_CACHE;
    }

    return null;
  }
}
