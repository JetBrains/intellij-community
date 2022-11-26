// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.SmartList
import com.intellij.util.containers.headTailOrNull
import org.jetbrains.annotations.ApiStatus

/**
 * Represents string which value is only partially known because of some variables concatenated with or interpolated into the string.
 *
 * For UAST languages it could be obtained from `UStringConcatenationsFacade.asPartiallyKnownString`.
 *
 * The common use case is a search for a place in partially known content to inject a reference.
 */
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

  val concatenationOfKnown: String
    get() {
      (segments.singleOrNull() as? StringEntry.Known)?.let { return it.value }

      val stringBuffer = StringBuffer()
      for (segment in segments) {
        if (segment is StringEntry.Known) stringBuffer.append(segment.value)
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

  constructor(string: String) : this(string, null, TextRange.EMPTY_RANGE)

  @JvmOverloads
  fun findIndexOfInKnown(pattern: String, startFrom: Int = 0): Int {
    var accumulated = 0
    for (segment in segments) {
      when (segment) {
        is StringEntry.Known -> {
          val i = segment.value.indexOf(pattern, startFrom - accumulated)
          if (i >= 0) return accumulated + i
          accumulated += segment.value.length
        }
        is StringEntry.Unknown -> {
        }
      }
    }
    return -1
  }

  private fun rangeForSubElement(parent: StringEntry, partRange: TextRange): TextRange =
    parent.rangeAlignedToHost
      ?.let { (host, hostRange) ->
        mapRangeToHostRange(host, hostRange, partRange)?.shiftLeft(parent.sourcePsi!!.startOffset - host.startOffset)
      }
    ?: partRange.shiftRight(parent.range.startOffset)

  private fun buildSegmentWithMappedRange(parent: StringEntry, value: String, rangeInPks: TextRange): StringEntry.Known =
    StringEntry.Known(value, parent.sourcePsi, rangeForSubElement(parent, rangeInPks))

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
            left.add(buildSegmentWithMappedRange(segment, leftPart, TextRange.from(0, leftPart.length)))

            return PartiallyKnownString(left) to PartiallyKnownString(
              ArrayList<StringEntry>(segments.lastIndex - i + 1).apply {
                if (rightPart.isNotEmpty())
                  add(buildSegmentWithMappedRange(segment, rightPart, TextRange.from(leftPart.length, rightPart.length)))
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

  fun split(pattern: String, escaperFactory: (CharSequence, String) -> SplitEscaper = { _, _ -> SplitEscaper.AcceptAll })
    : List<PartiallyKnownString> {

    tailrec fun collectPaths(result: MutableList<PartiallyKnownString>,
                             pending: MutableList<StringEntry>,
                             segments: List<StringEntry>): MutableList<PartiallyKnownString> {

      val (head, tail) = segments.headTailOrNull() ?: return result.apply {
        add(PartiallyKnownString(pending))
      }

      when (head) {
        is StringEntry.Unknown -> return collectPaths(result, pending.apply { add(head) }, tail)
        is StringEntry.Known -> {
          val value = head.value

          val stringParts = splitToTextRanges(value, pattern, escaperFactory).toList()
          if (stringParts.size == 1) {
            return collectPaths(result, pending.apply { add(head) }, tail)
          }
          else {
            return collectPaths(
              result.apply {
                add(PartiallyKnownString(
                  pending.apply {
                    add(StringEntry.Known(stringParts.first().substring(value), head.sourcePsi,
                      rangeForSubElement(head, stringParts.first())))
                  }))
                addAll(stringParts.subList(1, stringParts.size - 1).map {
                  PartiallyKnownString(it.substring(value), head.sourcePsi, rangeForSubElement(head, it))
                })
              },
              mutableListOf(StringEntry.Known(stringParts.last().substring(value), head.sourcePsi,
                rangeForSubElement(head, stringParts.last()))),
              tail
            )
          }

        }
      }

    }

    return collectPaths(SmartList(), mutableListOf(), segments)

  }

  fun mapRangeToHostRange(host: PsiElement, rangeInPks: TextRange): TextRange? =
    mapRangeToHostRange(host, ElementManipulators.getValueTextRange(host), rangeInPks)

  /**
   * @return the range in the given [host] (encoder-aware) that corresponds to the [rangeInPks] in the [valueIfKnown]
   * @param rangeInHost - range in the [host] if the only the part of the [host] should be considered.
   *                      useful if [host] corresponds to multiple [PartiallyKnownString]
   *
   * NOTE: currently supports only single-segment [rangeInPks]
   */
  private fun mapRangeToHostRange(host: PsiElement, rangeInHost: TextRange, rangeInPks: TextRange): TextRange? {

    fun getHostRangeEscapeAware(segmentRange: TextRange, inSegmentStart: Int, inSegmentEnd: Int): TextRange {
      if (host is PsiLanguageInjectionHost) {

        fun mkAttachments(): Array<Attachment> = arrayOf(
          Attachment("host.txt", kotlin.runCatching { host.text ?: "<null>" }.getOrElse { it.stackTraceToString() }),
          Attachment(kotlin.runCatching { host.containingFile?.virtualFile?.name  }.getOrNull() ?: "file.txt", 
                     kotlin.runCatching { host.containingFile?.text ?: "<null>" }.getOrElse { it.stackTraceToString() })
        )

        try {
          val escaper = host.createLiteralTextEscaper()
          val decode = escaper.decode(segmentRange, StringBuilder())
          if (decode) {
            val start = escaper.getOffsetInHost(inSegmentStart, segmentRange)
            val end = escaper.getOffsetInHost(inSegmentEnd, segmentRange)
            if (start != -1 && end != -1 && start <= end)
              return TextRange(start, end)
            else {
              logger<PartiallyKnownString>().error(
                "decoding of ${segmentRange} failed for $host : [$start, $end] inSegment = [$inSegmentStart, $inSegmentEnd]",
                *mkAttachments()
              )
              return TextRange(segmentRange.startOffset + inSegmentStart, segmentRange.startOffset + inSegmentEnd)
            }
          }
        }
        catch (e: Exception) {
          if (e is ControlFlowException) throw e
          logger<PartiallyKnownString>().error(
            "decoding of ${segmentRange} failed for $host inSegment = [$inSegmentStart, $inSegmentEnd]", e, *mkAttachments()
          )
        }
      }

      return TextRange(segmentRange.startOffset + inSegmentStart, segmentRange.startOffset + inSegmentEnd)
    }

    var accumulated = 0
    for (segment in segments) {
      if (segment !is StringEntry.Known) continue

      val (segmentHost, segmentRangeInHost) = segment.rangeAlignedToHost ?: continue
      if (segmentHost != host || !rangeInHost.contains(segmentRangeInHost)) continue // we don't support partial intersections

      val segmentEnd = accumulated + segment.value.length

      // assume that all content fits into one segment
      if (rangeInPks.startOffset >= accumulated && rangeInPks.endOffset <= segmentEnd) {
        val inSegmentStart = rangeInPks.startOffset - accumulated
        val inSegmentEnd = rangeInPks.endOffset - accumulated

        return getHostRangeEscapeAware(segmentRangeInHost, inSegmentStart, inSegmentEnd)
      }
      accumulated = segmentEnd
    }
    return null
  }

  /**
   * @return the range in the [valueIfKnown] that corresponds to given [host]
   */
  fun getRangeOfTheHostContent(host: PsiElement): TextRange? {
    var accumulated = 0
    var start = 0
    var end = 0
    var found = false
    for (segment in segments) {
      if (segment !is StringEntry.Known) continue
      if (segment.host == host) {
        if (!found) {
          found = true
          start = accumulated
        }
        end = accumulated + segment.value.length
      }
      accumulated += segment.value.length
    }
    if (found)
      return TextRange.from(start, end)
    else
      return null
  }

  /**
   * @return the cumulative range in the [originalHost] used by this [PartiallyKnownString]
   */
  fun getRangeInHost(originalHost: PsiElement): TextRange? {
    val ranges = segments.asSequence().mapNotNull { it.rangeAlignedToHost?.takeIf { it.first == originalHost } }.map { it.second }.toList()
    if (ranges.isEmpty()) return null
    return ranges.reduce(TextRange::union)
  }

  companion object {
    val empty = PartiallyKnownString(emptyList())
  }

}

@ApiStatus.Experimental
sealed class StringEntry {
  abstract val sourcePsi: PsiElement? // maybe it should be PsiLanguageInjectionHost and only for `Known` values

  /**
   * A range in the [sourcePsi] that corresponds to the content of this segment
   */
  abstract val range: TextRange

  class Known(val value: String, override val sourcePsi: PsiElement?, override val range: TextRange) : StringEntry() {
    override fun toString(): String = "StringEntry.Known('$value' at $range in $sourcePsi)"
  }

  class Unknown @JvmOverloads constructor(
    override val sourcePsi: PsiElement?,
    override val range: TextRange,
    @ApiStatus.Internal
    val possibleValues: Iterable<PartiallyKnownString>? = null
  ) : StringEntry() {
    override fun toString(): String = "StringEntry.Unknown(at $range in $sourcePsi)"
  }

  val host: PsiElement?
    get() = sourcePsi.takeIf { it.isSuitableHostClass() } ?: sourcePsi?.parent.takeIf { it.isSuitableHostClass() }

  val rangeAlignedToHost: Pair<PsiElement, TextRange>?
    get() {
      val entry = this
      val sourcePsi = entry.sourcePsi ?: return null
      if (sourcePsi.isSuitableHostClass()) return sourcePsi to entry.range
      val parent = sourcePsi.parent
      if (parent is PsiLanguageInjectionHost) { // Kotlin interpolated string, TODO: encapsulate this logic to range retrieval
        return parent to entry.range.shiftRight(sourcePsi.startOffsetInParent)
      }
      return null
    }

  private fun PsiElement?.isSuitableHostClass(): Boolean =
    when (this) {
      // this is primarily to workaround injections into YAMLKeyValue (which doesn't implement {@code PsiLanguageInjectionHost})
      is ContributedReferenceHost, is PsiLanguageInjectionHost -> true
      else -> false
    }
}

@ApiStatus.Experimental
fun splitToTextRanges(charSequence: CharSequence,
                      pattern: String,
                      escaperFactory: (CharSequence, String) -> SplitEscaper = { _, _ -> SplitEscaper.AcceptAll }): Sequence<TextRange> {
  var lastMatch = 0
  var lastSplit = 0
  val escaper = escaperFactory(charSequence, pattern)
  return sequence {
    while (true) {
      val start = charSequence.indexOf(pattern, lastMatch)
      if (start == -1) {
        yield(TextRange(lastSplit, charSequence.length))
        return@sequence
      }
      lastMatch = start + pattern.length
      if (escaper.filter(lastSplit, start)) {
        yield(TextRange(lastSplit, start))
        lastSplit = lastMatch
      }
    }
  }

}

@ApiStatus.Experimental
interface SplitEscaper {

  fun filter(lastSplit: Int, currentPosition: Int): Boolean

  object AcceptAll : SplitEscaper {
    override fun filter(lastSplit: Int, currentPosition: Int): Boolean = true
  }

}