// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.diff.chains.DiffRequestProducer
import kotlinx.coroutines.flow.Flow

interface ScrollableDiffRequestProducer : DiffRequestProducer {
  val scrollRequests: Flow<DiffLineLocation>
}