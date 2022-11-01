// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

import javax.swing.SwingUtilities

class RWAddedController : RWListenersController<RWActiveListener>() {
  fun enabled() {
    doWithListeners { listeners ->
      listeners.forEach { it.enabled() }
    }
  }

  fun disabled() {
    doWithListeners { listeners ->
      listeners.forEach { it.disabled() }
    }
  }

  fun initialize() {
    doWithListeners { listeners ->
      listeners.forEach { it.initialize() }
    }
  }
}

internal class RWSlotController : RWListenersController<RWSlotListener>() {
  fun rebuildPopup() {
    doWithListeners { listeners ->
      listeners.forEach { it.rebuildPopup() }
    }
  }

  fun slotAdded() {
    doWithListeners { listeners ->
      listeners.forEach { it.slotAdded() }
    }
  }

  fun slotRemoved(index: Int) {
    doWithListeners { listeners ->
      listeners.forEach { it.slotRemoved(index) }
    }
  }
}

internal class RWStateController : RWListenersController<RWStateListener>() {
  fun stateChanged(value: RWSlotManagerState) {
    doWithListeners { listeners ->
      listeners.forEach { it.stateChanged(value) }
    }
  }
}

abstract class RWListenersController<T> {
  private val listenersUnsafe = mutableListOf<T>()
  private val listenerLock = Object()
  protected fun doWithListeners(action: (MutableList<T>) -> Unit) {
    synchronized(listenerLock) {
      action(listenersUnsafe)
    }
  }

  fun addListener(listener: T) {
    doWithListeners { listeners ->
      listeners.add(listener)
    }
  }

  fun removeListener(listener: T) {
    SwingUtilities.invokeLater {
      doWithListeners { listeners ->
        listeners.remove(listener)
      }
    }
  }

  fun clear() {
    doWithListeners { listeners ->
      listeners.clear()
    }
  }
}