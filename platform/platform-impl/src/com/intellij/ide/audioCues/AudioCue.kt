// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

/** A sound and its presentation in the shared audio cue settings. */
@ApiStatus.Internal
class AudioCue(
  val id: @NonNls String,
  private val titleSupplier: Supplier<@Nls String>,
  val resourcePath: @NonNls String,
  /** The class loader of this class reads [resourcePath]. */
  val ownerClass: Class<*>,
  val settingsOrder: Int,
) {
  val title: @Nls String
    get() = titleSupplier.get()

  override fun toString(): String = id
}
