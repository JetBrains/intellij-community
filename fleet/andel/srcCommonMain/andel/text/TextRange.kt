// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias CharOffset = Long

@Serializable(with = TextRangeSerializer::class)
data class TextRange(val start: CharOffset, val end: CharOffset) {
  constructor(start: Int, end: Int) : this(start.toLong(), end.toLong())
  constructor(range: IntRange) : this(range.first, range.last + 1)

  val length: Long get() = end - start

  init {
    require(start <= end) { "invalid range start: $start end: $end" }
  }

  val isEmpty: Boolean
    get() = end == start

  operator fun contains(offset: Long): Boolean = offset in start until end
  operator fun contains(textRange: TextRange): Boolean = start <= textRange.start && textRange.end <= end
}

internal object TextRangeSerializer : KSerializer<TextRange> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TextRange", PrimitiveKind.STRING)
  private val serializer = PairSerializer(CharOffset.serializer(), CharOffset.serializer())

  override fun deserialize(decoder: Decoder): TextRange {
    return serializer.deserialize(decoder).let { TextRange(it.first, it.second) }
  }

  override fun serialize(encoder: Encoder, value: TextRange) {
    serializer.serialize(encoder, Pair(value.start, value.end))
  }
}

private val EMPTY_TEXT_RANGE = TextRange(0, 0)

/**
 * @return true if [this] and [textRange] intersect and [this] starts before [textRange] and ends inside [textRange]
 */
infix fun TextRange.intersectsBefore(textRange: TextRange): Boolean = start < textRange.start && end in (textRange.start + 1)..textRange.end

/**
 * @return true if [this] and [textRange] intersect and [textRange] starts before [this] and ends inside [this]
 */
infix fun TextRange.intersectsAfter(textRange: TextRange): Boolean = textRange intersectsBefore this

/**
 * @return true if [this] and [textRange] intersect and [textRange] contains [this]
 */
infix fun TextRange.inside(textRange: TextRange): Boolean = textRange.contains(this)

/**
 * @return true if [this] and [textRange] intersect and [this] contains [textRange]
 */
infix fun TextRange.outside(textRange: TextRange): Boolean = contains(textRange)

fun TextRange.shift(delta: Int) = TextRange(start + delta, end + delta)
fun TextRange.shift(delta: Long) = TextRange(start + delta, end + delta)
fun TextRange.isEmpty() = length <= 0
fun TextRange.isNotEmpty() = length > 0
fun TextRange.intersectsStrict(other: TextRange) = maxOf(start, other.start) < minOf(end, other.end)
fun TextRange.intersectsNonStrict(other: TextRange) = maxOf(start, other.start) <= minOf(end, other.end)
fun TextRange.coerce(limits: TextRange) = TextRange(maxOf(start, limits.start), minOf(end, limits.end))

enum class IntersectionType {
  Before,
  After,
  Inside,
  Outside,
  None
}

fun IntersectionType.invert(): IntersectionType = when (this) {
  IntersectionType.Before -> IntersectionType.After
  IntersectionType.After -> IntersectionType.Before
  IntersectionType.Inside -> IntersectionType.Outside
  IntersectionType.Outside -> IntersectionType.Inside
  IntersectionType.None -> IntersectionType.None
}

infix fun TextRange.intersect(textRange: TextRange): Pair<TextRange, IntersectionType> =
  when {
    this inside textRange -> this to IntersectionType.Inside
    this outside textRange -> textRange to IntersectionType.Outside
    this intersectsBefore textRange -> TextRange(textRange.start, this.end) to IntersectionType.Before
    this intersectsAfter textRange -> TextRange(this.start, textRange.end) to IntersectionType.After
    else -> EMPTY_TEXT_RANGE to IntersectionType.None
  }