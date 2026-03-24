// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.threadDumpParser.ThreadState
import org.jetbrains.annotations.ApiStatus

/**
 * Converts imported [ThreadState] instances into specialized [MergeableDumpItem] implementations.
 *
 * Return `null` to keep the default Java thread dump item conversion.
 */
@ApiStatus.Internal
interface ThreadDumpItemFactory {
  fun createDumpItem(threadState: ThreadState): MergeableDumpItem?

  companion object {
    private val EP_NAME: ExtensionPointName<ThreadDumpItemFactory> = ExtensionPointName("com.intellij.threadDumpItemFactory")

    fun createDumpItem(threadState: ThreadState): MergeableDumpItem {
      // TODO: extract JavaThreadDumpItem to factory as well
      return EP_NAME.computeSafeIfAny { it.createDumpItem(threadState) } ?: JavaThreadDumpItem(threadState)
    }
  }
}
