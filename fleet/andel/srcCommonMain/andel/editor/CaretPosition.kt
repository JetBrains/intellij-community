// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.text.TextRange
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.max
import kotlin.math.min

@Serializable(with=CaretPosition.Serializer::class)
data class CaretPosition(val offset: Long,
                         val selectionStart: Long,
                         val selectionEnd: Long) {

  class Serializer: DataSerializer<CaretPosition, List<Long>>(ListSerializer(Long.serializer())) {
    override fun fromData(data: List<Long>): CaretPosition {
      val (offset, selectionStart, selectionEnd) = data
      return CaretPosition(offset, selectionStart, selectionEnd)
    }

    override fun toData(value: CaretPosition): List<Long> = listOf(value.offset, value.selectionStart, value.selectionEnd)
  }

  constructor(offset: Long) : this(offset, offset, offset)

  init {
    require(offset in selectionStart..selectionEnd) {
      "offset outside of selection: offset=$offset, selection=$selectionStart..$selectionEnd"
    }
  }

  fun withoutSelection() =
    CaretPosition(offset, offset, offset)

  fun hasSelection(): Boolean =
    selectionStart != selectionEnd

  fun selectionOrNull(): TextRange? =
    if (selectionStart < selectionEnd)
      TextRange(selectionStart, selectionEnd)
    else
      null

  fun move(targetOffset: Long, expandSelection: Boolean): CaretPosition =
    when {
      !expandSelection ->
        CaretPosition(targetOffset, targetOffset, targetOffset)

      offset == selectionStart ->
        CaretPosition(targetOffset,
                      min(targetOffset, selectionEnd),
                      max(targetOffset, selectionEnd))

      offset == selectionEnd ->
        CaretPosition(targetOffset,
                      min(selectionStart, targetOffset),
                      max(selectionStart, targetOffset))

      //TODO: IDEA behaves differently
      else ->
        CaretPosition(targetOffset,
                      min(offset, targetOffset),
                      max(offset, targetOffset))
    }
}