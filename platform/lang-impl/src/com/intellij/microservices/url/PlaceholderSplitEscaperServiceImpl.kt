// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url

import com.intellij.psi.util.SplitEscaper
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets

internal class PlaceholderSplitEscaperServiceImpl : PlaceholderSplitEscaperService {
  override fun create(braces: List<UrlSpecialSegmentMarker>, input: CharSequence, pattern: String): SplitEscaper {
    return PlaceholderSplitEscaperImpl(braces, input, pattern)
  }
}

private class PlaceholderSplitEscaperImpl : SplitEscaper {
  private val toIgnore: IntSet

  constructor(braces: List<UrlSpecialSegmentMarker>, input: CharSequence, pattern: String) {
    val toIgnore = IntArrayList()
    for (beginEndPair in braces) {
      val matches = listOf(beginEndPair.prefix, beginEndPair.suffix, pattern)
      var position = 0
      var openBraces = 0

      while (true) {
        val (pos, str) = input.findAnyOf(matches, position) ?: break
        when (str) {
          beginEndPair.prefix -> openBraces++
          beginEndPair.suffix -> openBraces--
          pattern -> {
            if (openBraces > 0) {
              toIgnore.add(pos)
            }
          }
        }
        if (openBraces < 0) openBraces = 0
        position = pos + str.length
      }
    }

    this.toIgnore = if (toIgnore.isEmpty()) IntSets.EMPTY_SET else IntOpenHashSet(toIgnore)
  }

  override fun filter(lastSplit: Int, currentPosition: Int): Boolean = !toIgnore.contains(currentPosition)
}