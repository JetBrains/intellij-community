// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.components.PersistentStateComponent
import org.jetbrains.annotations.ApiStatus

/**
 * A [Configurable] that declares which [PersistentStateComponent]s back its state.
 *
 * This is used by the Settings editor to detect external changes to the backing state
 * while the settings dialog is open and the configurable has unsaved user edits.
 * When the user leaves the settings window (focus lost), the editor snapshots
 * the state of every modified configurable's backing components. On focus regain,
 * it compares the snapshot to the current state and logs/reports if an external
 * change has occurred.
 */
@ApiStatus.Internal
interface BackedByPersistentState {
  /**
   * Returns the [PersistentStateComponent]s whose persisted state this configurable reads from and writes to.
   */
  @ApiStatus.Internal
  @ApiStatus.OverrideOnly
  fun getBackingComponents(): Collection<PersistentStateComponent<*>>
}
