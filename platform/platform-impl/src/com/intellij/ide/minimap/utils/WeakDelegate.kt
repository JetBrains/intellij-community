// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.utils

import java.lang.ref.WeakReference

/**
 * Delegate class for simple events, which stores listeners as WeakReference.
 *
 * Usage example
 * <pre>
 * val delegate = WeakDelegate<String, Unit>()
 * delegate += { println("1>$it") }
 * delegate.notify("hello")
 * </pre>
 */
open class WeakDelegate<T, R> {

  private val listeners = mutableListOf<WeakReference<(T) -> R>>()

  operator fun plusAssign(listener: (T) -> R) {
    listeners.add(WeakReference(listener))
  }

  operator fun minusAssign(listener: (T) -> R) {
    listeners.removeIf { it.get() == listener }
  }

  fun notify(p: T) {
    val toRemove = mutableListOf<WeakReference<(T) -> R>>()

    val currentListeners = ArrayList(listeners)
    currentListeners.forEach {
      val listener = it.get()
      if (listener == null) {
        toRemove.add(it)
      }
      else {
        listener(p)
      }
    }

    listeners.removeAll(toRemove)
  }
}