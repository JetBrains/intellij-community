// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete.ranking

import com.intellij.platform.ml.impl.turboComplete.CompletionKind
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class RankedKind(
  val kind: CompletionKind,
  val relevance: Double?,
) {
  companion object {
    fun fromWeights(
      kindWeights: Iterable<Pair<CompletionKind, Double>>,
      negateWeight: Boolean,
    ): List<RankedKind> {
      return kindWeights
        .sortedBy { (_, weight) -> if (negateWeight) -weight else weight }
        .map { (kind, weight) -> RankedKind(kind, weight) }
    }
  }
}