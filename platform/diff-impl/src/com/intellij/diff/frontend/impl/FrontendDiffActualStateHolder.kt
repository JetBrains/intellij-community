// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.frontend.FrontendDiffViewer
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

/**
 * Backs [FrontendDiffViewer.isActual] and [FrontendDiffViewer.addActualStateListener] of a viewer implementation.
 *
 * A viewer is actual while the state its line mappings are built from still describes the documents as they are now. A local
 * viewer leaves that state around a rediff, a split-mode one also while the frontend documents catch up with the versions the
 * backend built the mapping from.
 */
@ApiStatus.Internal
class FrontendDiffActualStateHolder(isActual: Boolean = false) {
  private val listeners = mutableListOf<() -> Unit>()

  var isActual: Boolean = isActual
    private set

  fun addListener(disposable: Disposable, listener: () -> Unit) {
    listeners += listener
    Disposer.register(disposable, Disposable { listeners.remove(listener) })
  }

  /**
   * Records the current state and notifies the listeners when it changed.
   *
   * @param mappingChanged `true` when the mapping itself is a new one, so that the listeners are notified even though
   * [isActual] stays the same
   */
  fun update(isActual: Boolean, mappingChanged: Boolean = false) {
    val changed = this.isActual != isActual
    this.isActual = isActual
    if (changed || mappingChanged) {
      listeners.toList().forEach { it() }
    }
  }
}
