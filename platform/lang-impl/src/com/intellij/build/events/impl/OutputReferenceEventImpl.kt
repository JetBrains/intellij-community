// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.events.OutputId
import com.intellij.build.events.OutputReferenceEvent
import com.intellij.build.events.StartId

internal data class OutputReferenceEventImpl(
  override val startId: StartId,
  override val outputIds: List<OutputId>,
) : OutputReferenceEvent {

  override fun getId(): Nothing {
    throw UnsupportedOperationException("Output reference events do not have a id.")
  }

  override fun getParentId(): Nothing {
    throw UnsupportedOperationException("Output reference events do not have a parent id.")
  }

  override fun getEventTime(): Nothing {
    throw UnsupportedOperationException("Output reference events do not have a time.")
  }

  override fun getMessage(): Nothing {
    throw UnsupportedOperationException("Output reference events do not have a message.")
  }

  override fun getHint(): Nothing {
    throw UnsupportedOperationException("Output reference events do not have a hint.")
  }

  override fun getDescription(): Nothing {
    throw UnsupportedOperationException("Output reference events do not have a description.")
  }
}
