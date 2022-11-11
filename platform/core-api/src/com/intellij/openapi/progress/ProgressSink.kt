// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Represents an entity used to send progress updates to.
 * The client code is not supposed to read the progress updates, thus there are no getters.
 * It's up to implementors of this interface to decide what to do with these updates.
 */
@Experimental
@NonExtendable
interface ProgressSink {

  /**
   * Updates the current progress state. `null` value means "don't change corresponding property".
   */
  fun update(
    text: @ProgressText String? = null,
    details: @ProgressDetails String? = null,
    fraction: Double? = null,
  )

  /**
   * Updates current progress text.
   */
  fun text(text: @ProgressText String) {
    update(text = text)
  }

  /**
   * Updates current progress details.
   */
  fun details(details: @ProgressDetails String) {
    update(details = details)
  }

  /**
   * Updates current progress fraction.
   *
   * @param fraction a number between 0.0 and 1.0 reflecting the ratio of work that has already been done (0.0 for nothing, 1.0 for all)
   */
  fun fraction(fraction: Double) {
    update(fraction = fraction)
  }
}
