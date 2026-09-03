// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** The glibc allocator through a {@code malloc_trim} downcall. Linux only: the symbol is looked up on the first call, and a non-glibc libc has none. */
@ApiStatus.Internal
final class LinuxMalloc {
  private LinuxMalloc() { }

  /**
   * {@code malloc_trim(0)}: releases all free heap memory to the system.
   *
   * @return {@code true} when memory was released
   * @throws UnsatisfiedLinkError on every call when the libc has no {@code malloc_trim}
   */
  static boolean trim() {
    MethodHandle mallocTrim = Handles.MALLOC_TRIM;
    if (mallocTrim == null) {
      throw new UnsatisfiedLinkError("malloc_trim is not exported by this libc");
    }
    try {
      return (int)mallocTrim.invokeExact(0L) != 0;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    /** {@code int malloc_trim(size_t pad)}, or {@code null} when the libc has no such export; a missing symbol does not fail the class */
    static final @Nullable MethodHandle MALLOC_TRIM = find();

    private static @Nullable MethodHandle find() {
      Linker linker = Linker.nativeLinker();
      return linker.defaultLookup().find("malloc_trim")
        .map(symbol -> linker.downcallHandle(symbol, FunctionDescriptor.of(JAVA_INT, JAVA_LONG)))
        .orElse(null);
    }
  }
}
