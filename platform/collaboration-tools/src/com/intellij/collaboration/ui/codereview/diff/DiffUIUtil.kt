// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object DiffUIUtil {
  /**
   * Progress is delayed to avoid a flicker
   */
  val PROGRESS_DISPLAY_DELAY: Duration = 100.milliseconds

  val LOADING_PRODUCER = SimpleDiffRequestChain.DiffRequestProducerWrapper(LoadingDiffRequest())

  fun createErrorProducer(error: Throwable): DiffRequestProducer =
    SimpleDiffRequestChain.DiffRequestProducerWrapper(ErrorDiffRequest(error))
}