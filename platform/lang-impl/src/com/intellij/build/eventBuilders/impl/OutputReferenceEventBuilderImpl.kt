// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.OutputReferenceEventBuilder
import com.intellij.build.events.impl.OutputReferenceEventImpl

internal class OutputReferenceEventBuilderImpl(
  private val startId: Any,
  private val outputIds: List<Any>,
) : OutputReferenceEventBuilder {

  override fun build(): OutputReferenceEventImpl =
    OutputReferenceEventImpl(startId, outputIds)
}
