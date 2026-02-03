// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy
import kotlinx.coroutines.flow.Flow

interface DiffViewerScrollRequestProducer {
  val scrollRequests: Flow<DiffViewerScrollRequest>
}

sealed interface DiffViewerScrollRequest {
  companion object {
    fun toLine(location: DiffLineLocation): DiffViewerScrollRequest = DiffViewerLineScrollRequest(location)
    fun toFirstChange(): DiffViewerScrollRequest = DiffViewerChangeScrollRequest(ScrollToPolicy.FIRST_CHANGE)
    fun toLastChange(): DiffViewerScrollRequest = DiffViewerChangeScrollRequest(ScrollToPolicy.LAST_CHANGE)
  }
}

internal data class DiffViewerLineScrollRequest(val location: DiffLineLocation) : DiffViewerScrollRequest
internal data class DiffViewerChangeScrollRequest(val policy: ScrollToPolicy) : DiffViewerScrollRequest