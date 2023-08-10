// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.diff.util.Side

typealias DiffLineLocation = Pair<Side, Int>

class DiffMappedValue<out V>(val location: DiffLineLocation, val value: V) {
  val side: Side = location.first
  val lineIndex: Int = location.second
}