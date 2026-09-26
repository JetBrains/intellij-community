// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.OutputReferenceEvent
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

/**
 * A builder of the [OutputReferenceEvent].
 *
 * @see OutputReferenceEvent.builder
 */
@NonExtendable
interface OutputReferenceEventBuilder {

  /**
   * Builds the [OutputReferenceEvent] with the collected parameters.
   */
  @CheckReturnValue
  fun build(): OutputReferenceEvent
}