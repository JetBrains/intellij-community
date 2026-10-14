// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

/** The sounds of one event and their presentation in the shared sound signal settings. */
@ApiStatus.Internal
class SoundSignal(
  val id: @NonNls String,
  private val titleSupplier: Supplier<@Nls String>,
  val sounds: List<@NonNls String>,
  /** The class loader of this class reads [sounds]. */
  val ownerClass: Class<*>,
  val settingsOrder: Int,
  val group: SoundSignalGroup? = null,
  val preview: List<@NonNls String> = sounds,
) {
  constructor(
    id: @NonNls String,
    titleSupplier: Supplier<@Nls String>,
    sound: @NonNls String,
    ownerClass: Class<*>,
    settingsOrder: Int,
    group: SoundSignalGroup? = null,
  ) : this(id, titleSupplier, listOf(sound), ownerClass, settingsOrder, group)

  val title: @Nls String
    get() = titleSupplier.get()

  override fun toString(): String = id
}

@ApiStatus.Internal
class SoundSignalGroup(private val titleSupplier: Supplier<@Nls String>) {
  val title: @Nls String
    get() = titleSupplier.get()
}
