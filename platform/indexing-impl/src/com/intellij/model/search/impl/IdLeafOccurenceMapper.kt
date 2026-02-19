// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.TextOccurrence

internal object IdLeafOccurenceMapper : LeafOccurrenceMapper<TextOccurrence> {

  override fun mapOccurrence(occurrence: LeafOccurrence): Collection<TextOccurrence> {
    val (_, start, offsetInStart) = occurrence
    return listOf(TextOccurrence.of(start, offsetInStart))
  }
}
