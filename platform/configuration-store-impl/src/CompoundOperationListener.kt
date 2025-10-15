// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Contains multiple listeners and delegates method calls to them
 */
internal class CompoundOperationListener : OperationListener {

  private val listeners = CopyOnWriteArrayList<OperationListener>()

  override fun onWrite(fileSpec: String, content: ByteArray) {
    listeners.forEach { it.onWrite(fileSpec, content) }
  }

  override fun onDelete(fileSpec: String) {
    listeners.forEach { it.onDelete(fileSpec) }
  }

  /**
   * Adds a listener to delegate method calls to
   *
   * @param listener the listener
   */
  fun addOperationListener(listener: OperationListener) {
    listeners.add(listener)
  }

}
