// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.operation.*
import fleet.util.logging.logger
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable

@Serializable(with = MultiCaretData.Serializer::class)
data class MultiCaretData(val sortedCarets: List<Caret> = emptyList(),
                          val caretsById: Map<CaretId, Caret> = emptyMap(),
                          val mergedAnchors: Map<CaretId, CaretId> = emptyMap()) {
  companion object {
    val logger = logger<MultiCaretData>()
  }

  class Serializer : DataSerializer<MultiCaretData, Serializer.MultiCaretDataData>(MultiCaretDataData.serializer()) {
    @Serializable
    data class MultiCaretDataData(val sortedCarets: List<Caret>,
                                  val mergedCarets: Map<CaretId, CaretId>)

    override fun fromData(data: MultiCaretDataData): MultiCaretData {
      val caretsById = data.sortedCarets.associateBy { c -> c.caretId }
      val sortedCarets = data.sortedCarets
      val mergedAnchors = data.mergedCarets
      return MultiCaretData(sortedCarets = sortedCarets,
                            caretsById = caretsById,
                            mergedAnchors = mergedAnchors)
    }

    override fun toData(value: MultiCaretData): MultiCaretDataData {
      return MultiCaretDataData(sortedCarets = value.sortedCarets.toList(),
                                mergedCarets = value.mergedAnchors)
    }
  }

  val carets: List<Caret>
    get() = sortedCarets.toList()

  fun resolveAnchor(caretId: CaretId): Long {
    return requireNotNull(caret(caretId)).offset
  }

  fun caret(caretId: CaretId) = caretsById[mergedAnchors[caretId] ?: caretId]

  fun edit(operation: Operation, preserveVcol: Boolean = false): MultiCaretData = transformOnto(operation, Sticky.LEFT, preserveVcol)

  fun edit(newOffsetProvider: NewOffsetProvider): MultiCaretData {
    val updatingCarets = ArrayList<Caret>()
    val updatingMap = HashMap<CaretId, Caret>()
    var lastOffset = -1L
    var lastShifted = 0L

    fun mapOffset(offset: Long): Long {
      if (offset != lastOffset) {
        lastOffset = offset
        lastShifted = newOffsetProvider.getNewOffset(offset)
      }
      return lastShifted
    }

    sortedCarets.forEach { caret ->
      val newSelectionStart = mapOffset(caret.position.selectionStart)
      val newCaret = caret.move(CaretPosition(mapOffset(caret.offset), newSelectionStart, mapOffset(caret.position.selectionEnd)))
      updatingCarets.add(newCaret)
      updatingMap[newCaret.caretId] = newCaret
    }

    return MultiCaretData(
      sortedCarets = updatingCarets,
      caretsById = updatingMap,
      mergedAnchors = mergedAnchors
    )
  }

  fun transformOnto(operation: Operation, direction: Sticky, preserveVcol: Boolean = false): MultiCaretData {
    val updatingCarets = ArrayList<Caret>()
    val updatingMap = mutableMapOf<CaretId, Caret>()

    val points = ArrayList<IntervalPoint<Int, Unit>>(sortedCarets.size * 3)
    val caretsAtStart = ArrayList<Boolean>(sortedCarets.size)
    sortedCarets.forEachIndexed { index, caret ->
      val (caretPoints, caretAtStart) = caret.position.caretToPoints(index)
      points.addAll(caretPoints)
      caretsAtStart.add(caretAtStart)
    }
    val shiftedPoints = shiftPoints2(points, operation, direction)
    var cursor = 0
    for (i in 1..shiftedPoints.size) {
      val cursorId = shiftedPoints[cursor].id
      if (i == shiftedPoints.size || shiftedPoints[i].id != cursorId) {
        val updatedPosition = positionByPoints(shiftedPoints.subList(cursor, i), caretsAtStart[cursorId])
        val updatedCaret = sortedCarets[cursorId].let {
          it.move(updatedPosition, vCol = it.vCol.takeIf { preserveVcol })
        }
        updatingCarets.add(updatedCaret)
        updatingMap[updatedCaret.caretId] = updatedCaret
        cursor = i
      }
    }
    return MultiCaretData(
      sortedCarets = updatingCarets,
      caretsById = updatingMap,
      mergedAnchors = mergedAnchors
    )
  }

  fun addCarets(caretsToAdd: List<Caret>): MultiCaretData {
    val updatingMap = HashMap(caretsById)
    caretsToAdd.forEach { c ->
      updatingMap[c.caretId] = c
    }
    val updatedCarets = (sortedCarets + caretsToAdd).sortedBy(Caret::offset)
    return MultiCaretData(
      sortedCarets = updatedCarets,
      caretsById = updatingMap,
      mergedAnchors = mergedAnchors
    )
  }

  fun removeCarets(idsToRemove: Collection<CaretId>): MultiCaretData {
    val sortedCaretsPrime = ArrayList(sortedCarets)
    val caretsByIdPrime = HashMap(caretsById)
    val idsToRemoveSet = idsToRemove.toSet()
    for (caretId in idsToRemove) {
      caretsByIdPrime[caretId]?.let { caret ->
        sortedCaretsPrime.remove(caret)
        caretsByIdPrime.remove(caretId)
      }
    }
    val mergedAnchorsPrime = HashMap(mergedAnchors).apply {
      for ((key, value) in mergedAnchors) {
        val toRemove = key in idsToRemoveSet || value in idsToRemoveSet
        check(toRemove || value in caretsByIdPrime) {
          "caret ${key} was merged into ${value} which is now gone"
        }
        if (toRemove) {
          remove(key)
        }
      }
    }
    return MultiCaretData(
      sortedCarets = sortedCaretsPrime,
      caretsById = caretsByIdPrime,
      mergedAnchors = mergedAnchorsPrime
    )
  }

  fun ensureValidOffsets(charsCount: Long): MultiCaretData {
    return copy(sortedCarets = carets.map { caret ->
      val position = caret.position
      val validCaret = caret.copy(position = position.copy(
        offset = position.offset.coerceIn(0, charsCount),
        selectionStart = position.selectionStart.coerceIn(0, charsCount),
        selectionEnd = position.selectionEnd.coerceIn(0, charsCount)
      ))
      if (validCaret != caret) {
        logger.error(Throwable()) { "Attempted to set caret beyond text range (0, $charsCount): $caret" }
      }
      validCaret
    })
  }
}

fun MultiCaretData.mergeCoincidingCarets(): MultiCaretData {
  val updatingMergedAnchors = HashMap(mergedAnchors)
  val caretsToMerge = sortedCarets.groupBy { caret ->
    caret.position.offset
  }.map { (offset, coinciding) ->
    coinciding.drop(1).forEach { caretToRemove ->
      updatingMergedAnchors[caretToRemove.caretId] = coinciding.first().caretId
    }
    Caret(
      position = CaretPosition(
        offset = offset,
        selectionStart = coinciding.minOf { it.position.selectionStart },
        selectionEnd = coinciding.maxOf { it.position.selectionEnd }
      ),
      caretId = coinciding.first().caretId,
      vCol = coinciding.firstOrNull { caret -> caret.vCol != null }?.vCol,
      visible = coinciding.all { it.visible },
    )
  }
  return MultiCaretData(
    sortedCarets = caretsToMerge,
    caretsById = caretsById,
    mergedAnchors = updatingMergedAnchors
  )
}
