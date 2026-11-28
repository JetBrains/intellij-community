// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * The update flow [delegate] that also emits [LAF_CHANGE_UPDATE_ID] after each LaF change (see
 * [IconUpdateService.lafChanges]). So a collector, for example a Compose icon, paints again with the new theme.
 */
internal class ThemeAwareIconUpdateFlow(private val delegate: MutableIconUpdateFlow) : MutableIconUpdateFlow by delegate {
  override suspend fun collect(collector: FlowCollector<Int>) {
    val lafChanges = IconUpdateService.getInstance().lafChanges.map { LAF_CHANGE_UPDATE_ID }
    merge(delegate, lafChanges).collect(collector)
  }

  companion object {
    /** The update id of an update after a LaF change. The update ids of [delegate] are positive. */
    const val LAF_CHANGE_UPDATE_ID: Int = -1
  }
}
