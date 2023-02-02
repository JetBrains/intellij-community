// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

object DiffViewerUtil {
  fun <V : DiffViewerBase> createViewerReadyFlow(
    cs: CoroutineScope,
    viewer: V,
    isViewerGood: (V) -> Boolean = { true }
  ): StateFlow<Boolean> = callbackFlow {
    val listener = object : DiffViewerListener() {
      // for now this utility is only used for constant diffs
      // uncomment if diff can actually be changed on rediff
      /*override fun onBeforeRediff() {
        trySend(false)
      }*/

      override fun onAfterRediff() {
        trySend(isViewerGood(viewer))
      }
    }
    viewer.addListener(listener)
    send(isViewerGood(viewer))
    awaitClose {
      viewer.removeListener(listener)
    }
  }.stateIn(cs, SharingStarted.Lazily, isViewerGood(viewer))
}