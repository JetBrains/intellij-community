// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Global feature flag for editor lock-free typing
 * <a href="https://youtrack.jetbrains.com/issue/IJPL-54">IJPL-54</a>.
 * @see Elf
 */
public final class ElfFeatureFlag {
  // not final only for tests allowing to enable the feature flag temporarily
  private static volatile boolean IS_LOCK_FREE_TYPING_ENABLED;

  static {
    // elf registry key requires restart, hope this is enough to cache the value
    IS_LOCK_FREE_TYPING_ENABLED = Registry.is("editor.lockfree.typing.enabled", false);
  }

  /**
   * This flag only tells whether elf is enabled. It is not a contextual
   * check for the current execution. Use {@link Elf#isInElfScope()} when code needs
   * to know whether it is running inside lock-free typing, and
   * {@link Elf#isUnsupportedOperationGuardActive()} before calling operations that are
   * not supported yet during lock-free typing.
   */
  public static boolean isEnabled() {
    return IS_LOCK_FREE_TYPING_ENABLED;
  }

  @TestOnly
  public static void withEnabled(@NotNull Runnable action) {
    boolean oldValue = isEnabled();
    IS_LOCK_FREE_TYPING_ENABLED = true;
    try {
      action.run();
    } finally {
      IS_LOCK_FREE_TYPING_ENABLED = oldValue;
    }
  }
}
