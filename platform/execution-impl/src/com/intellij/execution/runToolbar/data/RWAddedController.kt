// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

class RWAddedController : RWListenersController<RWActiveListener>() {
  fun enabled() {
    listeners.forEach { it.enabled() }
  }

  fun disabled() {
    listeners.forEach { it.disabled() }
  }
}

internal class RWSlotController : RWListenersController<RWSlotListener>() {
  fun rebuildPopup() {
    listeners.forEach { it.rebuildPopup() }
  }

  fun slotAdded() {
    listeners.forEach { it.slotAdded() }
  }

  fun slotRemoved(index: Int) {
    listeners.forEach { it.slotRemoved(index) }
  }
}

internal class RWStateController : RWListenersController<RWStateListener>() {
  fun stateChanged(value: RWSlotManagerState) {
    listeners.forEach { it.stateChanged(value) }
  }
}

abstract class RWListenersController<T> {
  protected val listeners = mutableListOf<T>()

  fun addListener(listener: T) {
    listeners.add(listener)
  }

  fun removeListener(listener: T) {
    listeners.remove(listener)
  }

  fun clear() {
    listeners.clear()
  }
}