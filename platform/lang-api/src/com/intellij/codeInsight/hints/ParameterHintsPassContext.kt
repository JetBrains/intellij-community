// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import org.jetbrains.annotations.ApiStatus

/**
 * Holds a per-thread override of the parameter hint options for one run of `ParameterHintsPass`.
 *
 * An [InlayHintSettingsOverrider] can hide or enable a parameter hint option in one editor without changing the user settings.
 * `ParameterHintsPass` collects the overrider results into the map and calls [run].
 * [Option.get] reads this map while traversal collects the hints.
 */
@ApiStatus.Internal
object ParameterHintsPassContext {
  private val localOptionOverrides = ThreadLocal<Map<String, Boolean>?>()

  /**
   * Returns the option override for the current thread, or `null` outside [run].
   */
  @JvmStatic
  fun get(): Map<String, Boolean>? = localOptionOverrides.get()

  /**
   * Runs [traversal] on the current thread with [optionOverride] as the option override.
   * The keys of [optionOverride] are [Option.id] values.
   * When [traversal] ends, the previous override is restored.
   */
  @JvmStatic
  fun run(optionOverride: Map<String, Boolean>?, traversal: Runnable) {
    val previous = localOptionOverrides.get()
    localOptionOverrides.set(optionOverride)
    try {
      traversal.run()
    }
    finally {
      if (previous != null) {
        localOptionOverrides.set(previous)
      }
      else {
        localOptionOverrides.remove()
      }
    }
  }
}
