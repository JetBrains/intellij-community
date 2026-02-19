// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface Hideable {
  val hiddenState: StateFlow<Boolean>
  fun setHidden(hidden: Boolean)
}

/**
 * If there's at least one hidden item, hides them all, otherwise shows all items
 */
fun Sequence<Hideable>.syncOrToggleAll() {
  val noneShowing = none { !it.hiddenState.value }
  if (noneShowing) forEach {
    it.setHidden(false)
  }
  else forEach {
    it.setHidden(true)
  }
}