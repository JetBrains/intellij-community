// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class LookupPresentation private constructor(
  val positionStrategy: LookupPositionStrategy,
  val mostRelevantOnTop: Boolean
) {
  override fun toString(): String {
    return "LookupPresentation(positionStrategy=$positionStrategy, mostRelevantOnTop=$mostRelevantOnTop)"
  }

  class Builder(base: LookupPresentation? = null) {
    private var positionStrategy = base?.positionStrategy ?: LookupPositionStrategy.PREFER_BELOW
    private var mostRelevantOnTop = base?.mostRelevantOnTop ?: true

    /** See [LookupPositionStrategy] */
    fun withPositionStrategy(strategy: LookupPositionStrategy): Builder {
      positionStrategy = strategy
      return this
    }

    /**
     * If true, the first selected item most probably will be on top of the popup, otherwise - in the bottom.
     */
    fun withMostRelevantOnTop(onTop: Boolean): Builder {
      mostRelevantOnTop = onTop
      return this
    }

    fun build(): LookupPresentation {
      return LookupPresentation(positionStrategy, mostRelevantOnTop)
    }
  }
}