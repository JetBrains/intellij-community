// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext

/**
 * **Do not use it**. For low-level Swing code only.
 */
@ApiStatus.Internal
object RawSwingDispatcher : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    EventQueue.invokeLater(block)
  }

  override fun toString(): String = "Swing"
}