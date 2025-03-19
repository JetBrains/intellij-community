// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.editor.RangeMarker
import com.jetbrains.rd.util.lifetime.Lifetime

class CodeVisionModel {
  private val listeners = hashSetOf<Listener>()

  interface Listener {
    fun onLensesAddedOrUpdated(lensesMarkers: Iterable<RangeMarker>)
    fun onLensesRemoved(lensesMarkers: Iterable<RangeMarker>)
  }

  fun addOrUpdateLenses(lensesMarkers: Iterable<RangeMarker>) {
    for (listener in listeners) {
      listener.onLensesAddedOrUpdated(lensesMarkers)
    }
  }

  fun removeLenses(lensesMarkers: Iterable<RangeMarker>) {
    for (listener in listeners) {
      listener.onLensesRemoved(lensesMarkers)
    }
  }

  fun addCodeVisionListener(lifetime: Lifetime, listener: Listener) {
    listeners.add(listener)
    lifetime.onTermination {
      listeners.remove(listener)
    }
  }
}