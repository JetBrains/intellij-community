// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.annotations.ApiStatus

/**
 * A Compose [MutableState] that edits a persisted setting.
 *
 * The [value] is a working copy — the single source of UI truth — synced with the backing store only
 * through [apply] and [reset], so an in-progress edit stays pending until applied (matching the
 * `Configurable` contract). [isModified] reports whether the working value differs from the stored one.
 *
 * This is a plain Compose state, read and written with `by` like any other; what makes it a setting is
 * [ComposeSwingSearchableConfigurable.bind], which creates it and collects it to derive the page's
 * settings lifecycle. That is the only way to obtain one.
 *
 * @see com.intellij.ui.dsl.builder.MutableProperty
 * @see com.intellij.ui.dsl.builder.Cell.bindText
 */
@ApiStatus.Experimental
public class SettingState<T> internal constructor(
  private val load: () -> T,
  private val store: (T) -> Unit,
) : MutableState<T> by mutableStateOf(load()) {
  public val isModified: Boolean get() = value != load()

  public fun apply() {
    store(value)
  }

  public fun reset() {
    value = load()
  }
}
