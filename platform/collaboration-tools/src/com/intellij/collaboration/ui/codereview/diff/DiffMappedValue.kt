// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.diff.util.Side

typealias DiffLineLocation = Pair<Side, Int>

class DiffMappedValue<out V>(val location: DiffLineLocation, val value: V) {
  constructor(mapping: Pair<DiffLineLocation, V>) : this(mapping.first, mapping.second)
  val side: Side = location.first
  val lineIndex: Int = location.second
}

fun <R, V> DiffMappedValue<V>.mapValue(mapper: (V) -> R): DiffMappedValue<R> = DiffMappedValue(location, mapper(value))