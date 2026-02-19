// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import kotlin.math.max
import kotlin.math.min

fun MultiCaret.exhaustiveMoves(moves: List<Caret>): List<Caret> {
  if (carets.size == 1 && moves.size == 1) {
    return carets.map { caret ->
      moves.find { moved -> moved.caretId == caret.caretId } ?: caret
    }
  }
  else {
    val movesById = moves.associateBy { it.caretId }
    return carets.map { caret -> movesById[caret.caretId] ?: caret }
    // TODO I think fast-track of comparing moves.size vs carets.size is a good idea.
    //      However, it's not a bottleneck with he current impl for now.
  }
}

fun mergeCaretsBeforeMoves(positions: Collection<Caret>): List<Caret> {
  return buildList {
    val sortedPositions = positions.sortedBy { it.offset }
    val iterator = sortedPositions.listIterator()
    var previousPosition: Caret? = null
    while (iterator.hasNext()) {
      val position = iterator.next()
      if (previousPosition == null) {
        add(position)
      }
      else {
        when {
          previousPosition.position.selectionEnd > position.offset && previousPosition.position.offset <= position.position.selectionStart -> {
            set(size - 1, previousPosition.copy(position = previousPosition.position.copy(
              selectionStart = min(position.position.selectionStart, previousPosition.position.selectionStart),
              selectionEnd = max(position.position.selectionEnd, previousPosition.position.selectionEnd)
            )))
          }
          previousPosition.position.selectionEnd > position.position.selectionStart || previousPosition.offset == position.offset -> {
            set(size - 1, position.copy(position = position.position.copy(
              selectionStart = min(position.position.selectionStart, previousPosition.position.selectionStart),
              selectionEnd = max(position.position.selectionEnd, previousPosition.position.selectionEnd)
            )))
          }
          else -> {
            add(position)
          }
        }
      }
      previousPosition = position
    }
  }
}