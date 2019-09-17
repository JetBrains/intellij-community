// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.SmartList
import com.intellij.util.containers.toHeadAndTail
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed class StringEntry {
  abstract val sourcePsi: PsiElement? // maybe it should be PsiLanguageInjectionHost and only for `Known` values
  abstract val range: TextRange

  class Known(val value: String, override val sourcePsi: PsiElement?, override val range: TextRange) : StringEntry() {
    override fun toString(): String = "StringEntry.Known('$value' at $range in $sourcePsi)"
  }

  class Unknown(override val sourcePsi: PsiElement?, override val range: TextRange) : StringEntry() {
    override fun toString(): String = "StringEntry.Unknown(at $range in $sourcePsi)"
  }


  val rangeAlignedToHost: Pair<PsiLanguageInjectionHost, TextRange>?
    get() {
      val entry = this
      val sourcePsi = entry.sourcePsi ?: return null
      if (sourcePsi is PsiLanguageInjectionHost) return sourcePsi to entry.range
      val parent = sourcePsi.parent
      if (parent is PsiLanguageInjectionHost) { // Kotlin interpolated string, TODO: encapsulate this logic to range retrieval
        return parent to entry.range.shiftRight(sourcePsi.startOffsetInParent - ElementManipulators.getValueTextRange(parent).startOffset)
      }
      return null
    }
}

@ApiStatus.Experimental
class PartiallyKnownString(val segments: List<StringEntry>) {

  val valueIfKnown: String?
    get() {
      (segments.singleOrNull() as? StringEntry.Known)?.let { return it.value }

      val stringBuffer = StringBuffer()
      for (segment in segments) {
        when (segment) {
          is StringEntry.Known -> stringBuffer.append(segment.value)
          is StringEntry.Unknown -> return null
        }
      }
      return stringBuffer.toString()
    }

  override fun toString(): String = segments.joinToString { segment ->
    when (segment) {
      is StringEntry.Known -> segment.value
      is StringEntry.Unknown -> "<???>"
    }
  }

  constructor(single: StringEntry) : this(listOf(single))

  constructor(string: String, sourcePsi: PsiElement?, textRange: TextRange) : this(
    StringEntry.Known(string, sourcePsi, textRange))

  fun findIndexOfInKnown(pattern: String): Int {
    var accumulated = 0
    for (segment in segments) {
      when (segment) {
        is StringEntry.Known -> {
          val i = segment.value.indexOf(pattern)
          if (i >= 0) return accumulated + i
          accumulated += segment.value.length
        }
        is StringEntry.Unknown -> {
        }
      }
    }
    return -1
  }

  fun splitAtInKnown(splitAt: Int): Pair<PartiallyKnownString, PartiallyKnownString> {
    var accumulated = 0
    val left = SmartList<StringEntry>()
    for ((i, segment) in segments.withIndex()) {
      when (segment) {
        is StringEntry.Known -> {
          if (accumulated + segment.value.length < splitAt) {
            accumulated += segment.value.length
            left.add(segment)
          }
          else {
            val leftPart = segment.value.substring(0, splitAt - accumulated)
            val rightPart = segment.value.substring(splitAt - accumulated)
            left.add(StringEntry.Known(leftPart, segment.sourcePsi, TextRange.from(segment.range.startOffset, leftPart.length)))

            return PartiallyKnownString(left) to PartiallyKnownString(
              ArrayList<StringEntry>(segments.lastIndex - i + 1).apply {
                if (rightPart.isNotEmpty())
                  add(StringEntry.Known(rightPart, segment.sourcePsi,
                                        TextRange.from(segment.range.startOffset + leftPart.length, rightPart.length)))
                addAll(segments.subList(i + 1, segments.size))
              }
            )
          }
        }
        is StringEntry.Unknown -> {
          left.add(segment)
        }
      }
    }
    return this to empty
  }

  fun split(pattern: String): List<PartiallyKnownString> {

    tailrec fun collectPaths(result: MutableList<PartiallyKnownString>,
                             pending: MutableList<StringEntry>,
                             segments: List<StringEntry>): MutableList<PartiallyKnownString> {

      val (head, tail) = segments.toHeadAndTail() ?: return result.apply {
        add(
          PartiallyKnownString(pending))
      }

      when (head) {
        is StringEntry.Unknown -> return collectPaths(result, pending.apply { add(head) }, tail)
        is StringEntry.Known -> {
          val value = head.value

          val stringPaths = splitToTextRanges(value, pattern).toList()
          if (stringPaths.size == 1) {
            return collectPaths(result, pending.apply { add(head) }, tail)
          }
          else {
            return collectPaths(
              result.apply {
                add(PartiallyKnownString(
                  pending.apply {
                    add(StringEntry.Known(stringPaths.first().substring(value), head.sourcePsi,
                                          stringPaths.first()))
                  }))
                addAll(stringPaths.subList(1, stringPaths.size - 1).map {
                  PartiallyKnownString(it.substring(value), head.sourcePsi, it)
                })
              },
              mutableListOf(StringEntry.Known(stringPaths.last().substring(value), head.sourcePsi,
                                              stringPaths.last())),
              tail
            )
          }

        }
      }

    }

    return collectPaths(SmartList(), mutableListOf(), segments)

  }

  companion object {
    val empty = PartiallyKnownString(emptyList())
  }

}

@ApiStatus.Experimental
fun splitToTextRanges(charSequence: CharSequence, pattern: String): Sequence<TextRange> {
  var lastMatch = 0
  return sequence {
    while (true) {
      val start = charSequence.indexOf(pattern, lastMatch)
      if (start == -1) {
        yield(TextRange(lastMatch, charSequence.length))
        return@sequence
      }
      yield(TextRange(lastMatch, start))
      lastMatch = start + pattern.length
    }
  }

}