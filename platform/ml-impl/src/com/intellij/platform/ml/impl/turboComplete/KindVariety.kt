// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a family of completion kinds.
 * One [KindCollector] collects kinds from the same family (from Java, Kotlin K1, Kotlin K2, etc.)
 */
@ApiStatus.Internal
interface KindVariety {
  /**
   * Checks, if the kind variety can be collected withing the given parameters
   */
  fun kindsCorrespondToParameters(parameters: CompletionParameters): Boolean

  /**
   * Temporary workaround
   *
   * Currently, [SuggestionGenerator] is a "fixed"
   * version of a [com.intellij.codeInsight.completion.CompletionContributor].
   * We can't dynamically remove one contributor, so we need to filter it out, so we don't have duplicates
   * (suggestions from the contributor, and from the duplicating suggestion generator).
   * And to do this, we must know, what is the actual contributor, that we are filtering out.
   */
  val actualCompletionContributorClass: Class<*>
}