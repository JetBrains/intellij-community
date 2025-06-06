// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.operation

import andel.editor.CaretPosition
import andel.rope.Rope
import andel.text.CharOffset
import andel.text.IntersectionType
import andel.text.TextRange
import andel.text.intersect
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
sealed class Op {
  abstract val lenBefore: Long
  abstract val lenAfter: Long

  @Serializable
  data class Retain(val len: Long) : Op() {
    init {
      require(len >= 0) { "retain should have non-negative length" }
    }

    override val lenBefore: Long get() = len
    override val lenAfter: Long get() = len
  }

  @Serializable
  data class Replace(
    val delete: String,
    val insert: String
  ) : Op() {
    override val lenBefore: Long
      get() = delete.length.toLong()
    override val lenAfter: Long
      get() = insert.length.toLong()
  }
}

fun Op.isEmpty(): Boolean = lenBefore == 0L && lenAfter == 0L


class OpsIterator(
  private val owner: Any?,
  private var cursor: Rope.Cursor<Array<Op>>?,
  private val size: Int,
  private var index: Int = 0,
  private var elementIndex: Int = 0
): Iterator<Op> {
  sealed interface Pos {
    object Begin: Pos
    object End: Pos
    data class LenBefore(val offset: Int): Pos
    data class LenAfter(val offset: Int): Pos
  }

  fun location(): Pair<Long, Long> {
    var lenBefore = cursor!!.location(OperationMonoid.LenBefore).toLong()
    var lenAfter = cursor!!.location(OperationMonoid.LenAfter).toLong()
    val opsBatch = cursor!!.element
    for (i in 0 until elementIndex) {
      lenBefore += opsBatch[i].lenBefore
      lenAfter += opsBatch[i].lenAfter
    }

    return Pair(lenBefore, lenAfter)
  }

  override fun next(): Op {
    require(hasNext())

    if (elementIndex >= cursor!!.element.size) {
      cursor = cursor!!.next(owner)
      elementIndex = 0
    }
    val op = cursor!!.element[elementIndex]
    ++elementIndex
    ++index
    return op
  }

  override fun hasNext(): Boolean {
    return cursor != null && index < size
  }

  fun prev(): Op {
    require(hasPrev())

    if (elementIndex == 0) {
      cursor = cursor!!.prev(owner)
      elementIndex = cursor!!.element.size
    }
    --elementIndex
    --index
    return cursor!!.element[elementIndex]
  }

  fun hasPrev(): Boolean {
    return index > 0
  }

  fun scanTo(pos: Pos) {
    when (pos) {
      Pos.Begin -> {
        cursor = cursor!!.scan(owner, OperationMonoid.Count, 0)
        index = 0
        elementIndex = 0
      }
      Pos.End -> {
        cursor = cursor!!.scan(owner, OperationMonoid.Count, size)
        index = size
        elementIndex = cursor!!.element.size
      }
      is Pos.LenBefore -> {
        var c = cursor!!
        c = c.scan(owner, OperationMonoid.LenBefore, pos.offset)
        elementIndex = 0
        index = c.location(OperationMonoid.Count)
        var lenBefore = c.location(OperationMonoid.LenBefore).toLong()
        while (elementIndex < c.element.size && lenBefore + c.element[elementIndex].lenBefore < pos.offset) {
          lenBefore += c.element[elementIndex].lenBefore
          ++elementIndex
          ++index
        }
        cursor = c
      }
      is Pos.LenAfter -> {
        var c = cursor!!
        c = c.scan(owner, OperationMonoid.LenAfter, pos.offset)
        elementIndex = 0
        index = c.location(OperationMonoid.Count)
        var lenAfter = c.location(OperationMonoid.LenAfter).toLong()
        while (elementIndex < c.element.size && lenAfter + c.element[elementIndex].lenAfter < pos.offset) {
          lenAfter += c.element[elementIndex].lenAfter
          ++elementIndex
          ++index
        }
        cursor = c
      }
    }
  }
}

/**
 * After introducing Replace operation there still exists two asymmetries:
 * 1. Insert operations at the same place do not commute, so that {transform} function is not commutative
 * 2. Replace operation move the points laying inside to the left, not to the right
 *
 * Normalization is not needed after transformation because transformation doesn't slice ops.
 * TP1 property can be satisfied by choosing correct argument order of {transform} function.
 * TP2 property is replaced with weaker property of preserving points.
 *
 */
@Serializable(with = OperationSerializer::class)
class Operation(internal val rope: OpsRope) {
  constructor(ops: List<Op>): this(OperationMonoid.ropeOf(listOf(ops.toTypedArray())))

  val ops: Sequence<Op> get() = sequence { yieldAll(begin()) }

  fun begin(): OpsIterator {
    val owner = Any()
    val iterator = OpsIterator(owner, rope.cursor(owner), size)
    iterator.scanTo(OpsIterator.Pos.Begin)
    return iterator
  }

  fun end(): OpsIterator {
    val owner = Any()
    val iterator = OpsIterator(owner, rope.cursor(owner), size)
    iterator.scanTo(OpsIterator.Pos.End)
    return iterator
  }

  val size: Int
    get() = rope.size(OperationMonoid.Count)
  val isEmpty: Boolean
    get() = size == 0
  val lenBefore: Int
    get() = rope.size(OperationMonoid.LenBefore)
  val lenAfter: Int
    get() = rope.size(OperationMonoid.LenAfter)

  override fun equals(other: Any?): Boolean {
    return other is Operation && ops.toList().equals(other.ops.toList())
  }

  override fun hashCode(): Int {
    return ops.toList().hashCode()
  }

  fun copy(): Operation = Operation(ops.toList())

  companion object {
    /**
     * Empty operation applicable to any text.
     *
     * Any other operation is applicable only to the text of specified length.
     * Regular empty operations created by `Operation.empty(length)` are also applicable to the text of specified length.
     * This operation behaves as regular 'lengthy' empty operation:
     * - when applied to text of any arbitrary length
     * - when composed with another 'lengthy operation'
     *
     * There is no principal reason to have such exceptional empty operation, just convenient purposes.
     */
    fun empty() = Operation(emptyList())

    fun empty(totalLength: Long): Operation = Operation(Op.Retain(totalLength))

    fun deduceOperation(from: String, to: String): Operation {
      return replaceAt(0, from, to, from.length.toLong()).normalizeHard()
    }

    fun insertAt(offset: Long, text: String, totalLength: Long): Operation {
      require(offset <= totalLength) {
        "trying to insert at offset $offset text \"${text}\" but given totalLength of $totalLength"
      }
      return Operation(Op.Retain(offset),
                       Op.Replace("", text),
                       Op.Retain(totalLength - offset))
    }

    fun deleteAt(offset: Long, text: String, totalLength: Long): Operation {
      require(offset + text.length <= totalLength) {
        "trying to delete text \"${text}\" at offset $offset but given totalLength of $totalLength"
      }
      return Operation(Op.Retain(offset),
                       Op.Replace(text, ""),
                       Op.Retain(totalLength - text.length - offset))
    }

    fun replaceAt(offset: Long, oldText: String, newText: String, totalLength: Long, deduce: Boolean = false): Operation {
      require(offset + oldText.length <= totalLength) {
        "trying to delete text \"${oldText}\" at offset $offset but given totalLength of $totalLength"
      }
      val ops: MutableList<Op> = ArrayList()
      if (offset != 0L) {
        ops.add(Op.Retain(offset))
      }
      val replace = if (deduce) {
        deduce(oldText, newText)
      } else {
        deduceFast(oldText, newText)
      }
      ops.addAll(replace)
      val remaining = totalLength - oldText.length - offset
      if (remaining != 0L) {
        ops.add(Op.Retain(remaining))
      }
      return Operation(ops)
    }
  }
}

fun Operation(vararg op: Op): Operation {
  return Operation(op.toList())
}

fun Operation.isIdentity(): Boolean =
  this.ops.all { op ->
    when (op) {
      is Op.Retain -> true
      is Op.Replace -> op.insert.isEmpty() && op.delete.isEmpty()
    }
  }

fun Operation.isNotIdentity(): Boolean = !isIdentity()

@Suppress("NAME_SHADOWING")
fun deduceFast(deletes: String, inserts: String): List<Op> {
  val result = ArrayList<Op>()
  var deletes = deletes
  var inserts = inserts
  val commonPrefix = deletes.commonPrefixWith(inserts).length
  if (commonPrefix != 0) {
    result.add(Op.Retain(commonPrefix.toLong()))
    deletes = deletes.drop(commonPrefix)
    inserts = inserts.drop(commonPrefix)
  }
  val commonSuffix = deletes.commonSuffixWith(inserts).length
  deletes = deletes.dropLast(commonSuffix)
  inserts = inserts.dropLast(commonSuffix)
  if (deletes.isNotEmpty() || inserts.isNotEmpty()) {
    result.add(Op.Replace(deletes, inserts))
  }
  if (commonSuffix != 0) {
    result.add(Op.Retain(commonSuffix.toLong()))
  }
  return result
}

@Suppress("NAME_SHADOWING")
fun deduce(deletes: String, inserts: String): List<Op> {
  val result = ArrayList<Op>()
  var deletes = deletes
  var inserts = inserts

  while (deletes.isNotEmpty() || inserts.isNotEmpty()) {
    val commonPrefix = deletes.commonPrefixWith(inserts).length
    if (commonPrefix != 0) {
      result.add(Op.Retain(commonPrefix.toLong()))
      deletes = deletes.drop(commonPrefix)
      inserts = inserts.drop(commonPrefix)
    }
    else {
      val (deletesIdx, insertsIdx) = commonChar(deletes, inserts)
      val deletesPrefix = deletes.take(deletesIdx)
      val insertsPrefix = inserts.take(insertsIdx)

      check(deletesPrefix.isNotEmpty() || insertsPrefix.isNotEmpty())
      result.add(Op.Replace(deletesPrefix, insertsPrefix))
      deletes = deletes.drop(deletesIdx)
      inserts = inserts.drop(insertsIdx)
    }
  }

  return result
}

private const val sequentMin = 5
private const val distanceMax = 10
private fun similarFromThere(s1: String, i1: Int, s2: String, i2: Int): Boolean {
  return (0 until sequentMin).all { s1.getOrNull(i1 + it) == s2.getOrNull(i2 + it) }
}

private fun commonChar(s1: String, s2: String): Pair<Int, Int> {
  val nearestSimilarity = s2.take(distanceMax).withIndex().mapNotNull { (i2, c2) ->
    val found = s1.take(distanceMax).withIndex().firstOrNull { (i1, c1) -> c1 == c2 && similarFromThere(s1, i1, s2, i2) }
    if (found != null) Pair(found.index, i2) else null
  }.minByOrNull { min(it.first, it.second) }

  if (nearestSimilarity != null)
    return nearestSimilarity

  s2.withIndex().filter { (i, _) -> i % 10 == 0 }.forEach { (i2, c2) ->
    val found = s1.withIndex().firstOrNull { (i1, c1) -> c1 == c2 && similarFromThere(s1, i1, s2, i2) }
    if (found != null) {
      return Pair(found.index, i2)
    }
  }

  return Pair(s1.length, s2.length)
}


fun Op.invert(): Op {
  return when (this) {
    is Op.Retain -> this
    is Op.Replace -> Op.Replace(delete = this.insert, insert = this.delete)
  }
}

fun Operation.invert(): Operation {
  return Operation(this.ops.map(Op::invert).toList())
}

fun Operation.compose(subsequent: Operation): Operation {
  fun pushRetain(accum: MutableList<Op>, length: Long) {
    if (length == 0L) return
    val last = accum.lastOrNull()
    if (last is Op.Retain) accum[accum.size - 1] = Op.Retain(last.len + length)
    else accum.add(Op.Retain(length))
  }
  fun pushReplace(accum: MutableList<Op>, delete: StringBuilder, insert: StringBuilder, middle: StringBuilder) {
    if (delete.isEmpty() && insert.isEmpty()) return
    accum.add(Op.Replace(delete.toString(), insert.toString()))
    delete.clear()
    insert.clear()
    middle.clear()
  }

  if (this.isIdentity()) return subsequent
  if (subsequent.isIdentity()) return this
  require(this.lenAfter == subsequent.lenBefore) {
    "cannot compose $this with $subsequent: length ${this.lenAfter} does not match ${subsequent.lenBefore}"
  }
  val op1Iter = this.ops.iterator()
  val op2Iter = subsequent.ops.iterator()
  var op1: Op? = null
  var op2: Op? = null
  var consume2: Int = 0
  var consume1Offset: Long = 0
  var consume2Offset: Long = 0
  val lastOpMiddle = StringBuilder()
  val lastOp1 = StringBuilder()
  val lastOp2 = StringBuilder()
  var lastOpEnd: Long = 0
  val result = ArrayList<Op>(this.size + subsequent.size)
  while (op1Iter.hasNext() || op2Iter.hasNext()) {
    if (op1Iter.hasNext() && consume1Offset <= consume2Offset) {
      op1 = op1Iter.next()
      val op = op1
      val counterOp = op2
      when (op) {
        is Op.Replace -> {
          val opMiddleText = op.insert
          if (counterOp is Op.Retain) {
            lastOp2.append(opMiddleText.substring(0, min(opMiddleText.length, (consume2Offset - consume1Offset).toInt())))
          }
          val unmatchedMiddleLength = (lastOpEnd - consume1Offset).toInt()
          val matchLength = min(opMiddleText.length, unmatchedMiddleLength)
          require(matchLength >= 0)
          require(opMiddleText.regionMatches(0, lastOpMiddle, lastOpMiddle.length - unmatchedMiddleLength, matchLength)) {
            "cannot compose $this with $subsequent: '${opMiddleText.substring(0, matchLength)}' doesn't match '${
              lastOpMiddle.substring(lastOpMiddle.length - unmatchedMiddleLength, lastOpMiddle.length - unmatchedMiddleLength + matchLength)
            }' at offset $consume1Offset"
          }
          lastOpMiddle.append(opMiddleText.subSequence(matchLength, opMiddleText.length))
          lastOp1.append(op.delete)
          lastOpEnd = max(consume1Offset + opMiddleText.length, lastOpEnd)
        }
        is Op.Retain -> {
          when {
            op.len == 0L -> {
            }
            lastOpEnd < consume2Offset && op.len > 0 -> {
              val nextLastOpEnd = min(consume1Offset + op.len, consume2Offset)
              pushReplace(result, lastOp1, lastOp2, lastOpMiddle)
              pushRetain(result, nextLastOpEnd - lastOpEnd)
              lastOpEnd = nextLastOpEnd
            }
            else -> {
              val counterText = (counterOp as? Op.Replace)?.delete ?: ""
              val counterRetainLen = min(op.len, consume2Offset - consume1Offset).toInt()
              val counterRetainStart = counterText.length - (consume2Offset - consume1Offset).toInt()
              lastOp1.append(counterText.substring(counterRetainStart, counterRetainStart + counterRetainLen))
            }
          }
        }
      }
      consume1Offset += op.lenAfter
    }
    else {
      require(op2Iter.hasNext()) {
        "cannot compose $this with $subsequent, subsequent.ops.size=${subsequent.size}, consume2=$consume2"
      }
      op2 = op2Iter.next()
      consume2 += 1

      val op = op2
      val counterOp = op1
      when (op) {
        is Op.Replace -> {
          val opMiddleText = op.delete
          if (counterOp is Op.Retain) {
            lastOp1.append(opMiddleText.substring(0, min(opMiddleText.length, (consume1Offset - consume2Offset).toInt())))
          }
          val unmatchedMiddleLength = (lastOpEnd - consume2Offset).toInt()
          val matchLength = min(opMiddleText.length, unmatchedMiddleLength)
          require(matchLength >= 0)
          require(opMiddleText.regionMatches(0, lastOpMiddle, lastOpMiddle.length - unmatchedMiddleLength, matchLength)) {
            "cannot compose $this with $subsequent: '${opMiddleText.substring(0, matchLength)}' doesn't match '${
              lastOpMiddle.substring(lastOpMiddle.length - unmatchedMiddleLength, lastOpMiddle.length - unmatchedMiddleLength + matchLength)
            }' at offset $consume2Offset"
          }
          lastOpMiddle.append(opMiddleText.subSequence(matchLength, opMiddleText.length))
          lastOp2.append(op.insert)
          lastOpEnd = max(consume2Offset + opMiddleText.length, lastOpEnd)
        }
        is Op.Retain -> {
          when {
            op.len == 0L -> {
            }
            lastOpEnd < consume1Offset -> {
              val nextLastOpEnd = min(consume2Offset + op.len, consume1Offset)
              pushReplace(result, lastOp1, lastOp2, lastOpMiddle)
              pushRetain(result, nextLastOpEnd - lastOpEnd)
              lastOpEnd = nextLastOpEnd
            }
            else -> {
              val counterText = (counterOp as? Op.Replace)?.insert ?: ""
              val counterRetainLen = min(op.len, consume1Offset - consume2Offset).toInt()
              val counterRetainStart = counterText.length - (consume1Offset - consume2Offset).toInt()
              lastOp2.append(counterText.substring(counterRetainStart, counterRetainStart + counterRetainLen))
            }
          }
        }
      }
      consume2Offset += op.lenBefore
    }
  }
  require(consume1Offset == consume2Offset) {
    "can't compose $this with $subsequent: length $consume1Offset doesn't match with $consume2Offset"
  }
  pushReplace(result, lastOp1, lastOp2, lastOpMiddle)
  pushRetain(result, consume1Offset - lastOpEnd)
  result.trimToSize()
  return Operation(result)
}

fun composeAll(operations: Collection<Operation>): Operation {
  if (operations.size == 1) return operations.single()
  data class Event(val offset: Long, val op: Op.Replace)

  val events: MutableList<Event> = ArrayList()
  var totalLength: Long = 0
  for (o in operations) {
    var offset = 0L
    for (op in o.ops) {
      when (op) {
        is Op.Retain -> offset += op.len
        is Op.Replace -> {
          events.add(Event(offset, op))
          offset += op.delete.length
        }
      }
    }
    if (totalLength != 0L) {
      require(totalLength == offset || offset == 0L)
    }
    else {
      totalLength = offset
    }
  }
  events.sortBy { it.offset }

  var offset = 0L
  var longestDelete: Event? = null
  val deleteCombined = StringBuilder()
  val pendingInserts: MutableCollection<String> = ArrayList()
  val resultOps: MutableList<Op> = ArrayList()

  fun deleteUntil(): Long = longestDelete!!.offset + longestDelete!!.op.delete.length

  fun flushDelete() {
    require(longestDelete != null)
    resultOps.add(Op.Replace(delete = deleteCombined.toString(), insert = pendingInserts.joinToString(separator = "")))
    deleteCombined.clear()
    pendingInserts.clear()
    offset = deleteUntil()
    longestDelete = null
  }

  for (e in events) {
    if (longestDelete != null) {
      val endOffset = deleteUntil()
      if (endOffset <= e.offset) {
        flushDelete()
      }
    }

    if (e.offset > offset && longestDelete == null) {
      resultOps.add(Op.Retain(e.offset - offset))
      offset = e.offset
    }

    val deleteString = e.op.delete
    if (deleteString.isNotEmpty()) {
      if (longestDelete == null) {
        longestDelete = e
        deleteCombined.append(deleteString)
      }
      else {
        val myEndOffset = e.offset + deleteString.length
        val storedEndOffset = deleteUntil()

        val currentOffsetAtStored = deleteCombined.length - (storedEndOffset - e.offset)
        require(deleteCombined.regionMatches(currentOffsetAtStored.toInt(), deleteString, 0,
                                             minOf((storedEndOffset - e.offset).toInt(), deleteString.length)))
        if (myEndOffset > storedEndOffset) {
          deleteCombined.append(deleteString.substring((deleteString.length - (myEndOffset - storedEndOffset)).toInt()))
          longestDelete = e
        }
      }
    }
    val insertString = e.op.insert
    if (insertString.isNotEmpty()) {
      if (longestDelete == null) {
        resultOps.add(Op.Replace("", insertString))
      }
      else {
        pendingInserts.add(insertString)
      }
    }
    offset = e.offset
  }

  if (longestDelete != null) {
    flushDelete()
  }
  if (offset < totalLength) {
    resultOps.add(Op.Retain(totalLength - offset))
  }
  require(longestDelete == null)
  require(pendingInserts.isEmpty())
  return Operation(resultOps)
}

fun Iterable<Operation>.compose(): Operation {
  return this.fold(Operation.empty(), Operation::compose)
}

enum class Sticky {
  LEFT, RIGHT
}

fun Operation.toAffectedRanges(): Sequence<TextRange> = sequence {
  var offset = 0L
  ops.forEach { op ->
    when (op) {
      is Op.Retain -> offset += op.len
      is Op.Replace -> {
        yield(TextRange(offset, offset + op.lenBefore))
        offset += op.lenBefore
      }
    }
  }
}

/**
 * Merges two operations targeting the same initial text
 **/
operator fun Operation.plus(operation: Operation): Operation {
  return this.compose(operation.transform(this, Sticky.RIGHT))
}

fun Operation.normalizeHard(): Operation {
  val deducedOps = sequence<Op> {
    val deletes = StringBuilder()
    val inserts = StringBuilder()

    suspend fun SequenceScope<Op>.flushDeducedReplacement() {
      yieldAll(deduce(deletes.toString(), inserts.toString()))
      deletes.clear()
      inserts.clear()
    }

    for (op in this@normalizeHard.ops) {
      if (op.isEmpty()) continue
      when (op) {
        is Op.Retain -> {
          flushDeducedReplacement()
          yield(op)
        }
        is Op.Replace -> {
          deletes.append(op.delete)
          inserts.append(op.insert)
        }
      }
    }
    flushDeducedReplacement()
  }

  return Operation(deducedOps.normalizeSoft())
}

fun Operation.normalizeSoft(): Operation {
  return Operation(ops.normalizeSoft())
}

private fun Sequence<Op>.normalizeSoft(): List<Op> {
  return buildList {
    var accOp: Op? = null
    for (op in this@normalizeSoft) {
      when {
        op.isEmpty() -> {}
        accOp is Op.Retain && op is Op.Retain -> {
          accOp = Op.Retain(accOp.len + op.len)
        }
        accOp is Op.Replace && op is Op.Replace -> {
          accOp = Op.Replace(accOp.delete + op.delete, accOp.insert + op.insert)
        }
        else -> {
          if (accOp != null) add(accOp)
          accOp = op
        }
      }
    }
    if (accOp != null) add(accOp)
  }
}

fun CaretPosition.transformOnto(operation: Operation, direction: Sticky): CaretPosition {
  val (points, caretAtStart) = caretToPoints(Unit)
  val shifted = shiftPoints2(points, operation, direction)
  return positionByPoints(shifted, caretAtStart)
}

internal fun positionByPoints(shifted: List<IntervalPoint<*, *>>, caretAtStart: Boolean): CaretPosition {
  val offset = when {
    shifted.size == 1 -> shifted[0].offset
    shifted.size == 2 && caretAtStart -> shifted[0].offset
    shifted.size == 2 && !caretAtStart -> shifted[1].offset
    else -> shifted[1].offset
  }
  return CaretPosition(offset, shifted.first().offset, shifted.last().offset)
}

internal fun <K> CaretPosition.caretToPoints(key: K): Pair<List<IntervalPoint<K, Unit>>, Boolean> = when {
  selectionStart == selectionEnd ->
    listOf(IntervalPoint(key, selectionStart, null, null)) to true
  offset == selectionStart ->
    listOf(IntervalPoint(key, selectionStart, Sticky.RIGHT, BorderKind.START),
           IntervalPoint(key, selectionEnd, Sticky.LEFT, BorderKind.END)) to true
  offset == selectionEnd ->
    listOf(IntervalPoint(key, selectionStart, Sticky.RIGHT, BorderKind.START),
           IntervalPoint(key, selectionEnd, Sticky.LEFT, BorderKind.END)) to false
  else -> listOf(
    IntervalPoint(key, selectionStart, Sticky.RIGHT, BorderKind.START),
    IntervalPoint(key, offset, null, null),
    IntervalPoint(key, selectionEnd, Sticky.LEFT, BorderKind.END)) to false
}

fun CharOffset.transformOnto(operation: Operation, direction: Sticky): Long {
  return shiftPoints2(listOf(IntervalPoint(Unit, this, null, null)), operation, direction).single().offset
}

/**
 * @param emptyRangeStickiness to hint preferred stickiness for the case, when TextRange is empty. In this case the range is rebased
 *                             the way like an offset is.
 */
fun TextRange.transformOnto(operation: Operation, direction: Sticky, emptyRangeStickiness: Sticky? = null): TextRange {
  val points = if (start == end) {
    listOf(IntervalPoint(Unit, start, emptyRangeStickiness, null))
  }
  else {
    listOf(
      IntervalPoint(Unit, start, Sticky.RIGHT, BorderKind.START),
      IntervalPoint(Unit, end, Sticky.LEFT, BorderKind.END)
    )
  }
  val updatedPoints = shiftPoints2(points, operation, direction)
  require(updatedPoints.size in 1..2)
  require(updatedPoints.first().offset <= updatedPoints.last().offset)
  return TextRange(updatedPoints.first().offset, updatedPoints.last().offset)
}

fun canTransform(operation1: Operation, operation2: Operation): Boolean {
  if (operation1.isEmpty || operation2.isEmpty) return true
  return operation1.lenBefore == operation2.lenBefore
}

fun Operation.transform(operation2: Operation, direction: Sticky): Operation {
  return transform(this, operation2) { direction }
}

internal fun transform(operation1: Operation, operation2: Operation, direction: () -> Sticky): Operation {
  if (operation2.isEmpty || operation1.isEmpty) return operation1
  require(canTransform(operation1, operation2)) {
    "cannot transform $operation1 onto $operation2: length ${operation1.lenBefore} does not match ${operation2.lenBefore}"
  }
  val opIterator: Iterator<Op> = operation2.ops.iterator()
  var currentDelta = 0L
  var currentCursor = 0L

  var currentDeletion = ""
  var currentInsertion = ""
  var currentDeletionOffset: Long? = null
  fun consumeOperation(): Op {
    val op = opIterator.next()
    when (op) {
      is Op.Retain -> {
        currentCursor += op.len
      }
      is Op.Replace -> {
        if (currentDeletionOffset != null) {
          val currentDeletionPrev = currentDeletion
          val (removeStart, removeEnd) = currentCursor - currentDeletionOffset!! to currentCursor - currentDeletionOffset!! + op.delete.length
          if (removeEnd < currentDeletion.length) {
            currentInsertion += op.insert
          }
          currentDeletion = currentDeletion.replaceRange(removeStart.toInt(), removeEnd.toInt().coerceAtMost(currentDeletion.length),
                                                         op.insert)
          currentDeletionOffset = currentDeletionOffset!! - currentDeletion.length + currentDeletionPrev.length
        }
        currentCursor += op.delete.length
        currentDelta -= op.delete.length
        currentDelta += op.insert.length
      }
    }
    return op
  }

  var currentOp: Op? = null

  fun shiftPoint(point: Point): Long {
    var resultShift: Long? = null
    do {
      if (point.effectiveOffset(direction) >= currentCursor) {
        if (opIterator.hasNext()) {
          currentOp = consumeOperation()
        }
        else {
          require(point.offset == currentCursor && (point.sticky ?: direction()) == Sticky.RIGHT) {
            "Some offsets were not covered by operation: operation = $operation2, point = $point"
          }
          resultShift = currentDelta
        }
      }
      else {
        resultShift = when (val op = currentOp) {
          null -> {
            require(point.offset == 0L && (point.sticky ?: direction()) == Sticky.LEFT) {
              "Point before the text: operation = $operation2, point = $point"
            }
            0L
          }
          is Op.Retain -> currentDelta
          is Op.Replace -> {
            val rightBorder = currentDelta + currentCursor - point.offset
            when (point.sticky) {
              Sticky.LEFT -> rightBorder - op.insert.length
              Sticky.RIGHT -> rightBorder
              null -> when (point.offset) {
                currentCursor - op.delete.length -> rightBorder - op.insert.length
                currentCursor -> rightBorder
                else -> when (direction()) {
                  Sticky.LEFT -> rightBorder - op.insert.length
                  Sticky.RIGHT -> rightBorder
                }
              }
            }
          }
        }
      }
    }
    while (resultShift == null)
    return resultShift
  }

  var cursor1: Long = 0
  var lastEnd1: Long = 0
  val result = mutableListOf<Op>()
  for (op1 in operation1.ops) {
    if (op1 is Op.Replace) {
      val (shiftL, shiftR) = TextRange(cursor1, cursor1 + op1.lenBefore).run {
        currentInsertion = ""
        if (this.start != this.end) {
          val movedStart = this.start + shiftPoint(Point(this.start, Sticky.RIGHT))
          currentDeletion = op1.delete
          currentDeletionOffset = cursor1
          when (val op = currentOp) {
            is Op.Replace -> {
              val currentDeletionPrev = currentDeletion
              currentDeletion = currentDeletion.removeRange(
                (currentCursor - op.delete.length - currentDeletionOffset!!).coerceAtLeast(0).toInt(),
                ((currentCursor - op.delete.length - currentDeletionOffset!!).toInt() + op.delete.length).coerceAtMost(
                  currentDeletion.length))
              currentDeletionOffset = currentDeletionOffset!! - currentDeletion.length + currentDeletionPrev.length
            }
            else -> {}
          }
          val movedEnd = this.end + shiftPoint(Point(this.end, Sticky.LEFT))
          if (movedStart <= movedEnd) {
            return@run Pair(movedStart - this.start, movedEnd - this.end)
          }
          else {
            val currentOpStart = currentCursor - currentOp!!.lenBefore
            val single = when {
              this.start == currentOpStart && this.end < currentCursor -> movedEnd
              this.start > currentOpStart && this.end == currentCursor -> movedStart
              else -> when (direction()) {
                Sticky.LEFT -> movedEnd
                Sticky.RIGHT -> movedStart
              }
            }
            return@run Pair(single - this.start, single - this.end)
          }
        }
        val single = shiftPoint(Point(this.start, null))
        return@run Pair(single, single)
      }
      val newL = cursor1 + shiftL
      val newR = cursor1 + shiftR + op1.lenBefore
      result.add(Op.Retain(newL - lastEnd1))
      val insertion = when (direction()) {
        Sticky.LEFT -> op1.insert + currentInsertion
        Sticky.RIGHT -> currentInsertion + op1.insert
      }
      result.add(op1.copy(delete = currentDeletion.substring(0, (newR - newL).toInt()), insert = insertion))
      currentDeletion = ""
      currentDeletionOffset = null
      lastEnd1 = newR
    }
    cursor1 += op1.lenBefore
  }
  result.add(Op.Retain(cursor1 + shiftPoint(Point(cursor1, Sticky.RIGHT)) - lastEnd1))
  return Operation(result)
}

fun Sticky?.intValue(): Int {
  return when (this) {
    Sticky.LEFT -> -1
    null -> 0
    Sticky.RIGHT -> 1
  }
}

fun Sticky.opposite(): Sticky {
  return when (this) {
    Sticky.LEFT -> Sticky.RIGHT
    Sticky.RIGHT -> Sticky.LEFT
  }
}

enum class BorderKind { START, END }

private fun BorderKind.opposite(): BorderKind {
  return when (this) {
    BorderKind.START -> BorderKind.END
    BorderKind.END -> BorderKind.START
  }
}

private interface WithIdentifier<K> {
  val myId: K
}

private typealias BorderId<K> = Pair<K, BorderKind?>

data class PointData<K, V>(
  val id: K,
  val sticky: Sticky?,
  val borderKind: BorderKind?,
  val payload: V,
) : WithIdentifier<Pair<K, BorderKind?>> {
  init {
    require(sticky != null || borderKind == null)
  }

  override val myId: Pair<K, BorderKind?>
    get() = id to borderKind

  val greedy: Boolean
    get() {
      requireNotNull(borderKind)
      return when (borderKind) {
        BorderKind.START -> sticky == Sticky.LEFT
        BorderKind.END -> sticky == Sticky.RIGHT
      }
    }

  fun <L> withId(id: L): PointData<L, V> {
    return PointData(id, sticky, borderKind, payload)
  }

  fun toNeutralPoint(): PointData<K, V> {
    require(!greedy)
    return copy(sticky = null, borderKind = null)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PointData<*, *>) return false
    if (myId != other.myId) return false
    if (borderKind != other.borderKind) return false
    return true
  }

  override fun hashCode(): Int {
    return fleet.util.hash(myId, borderKind)
  }
}

data class IntervalPoint<K, V>(
  val offset: Long,
  val pointData: PointData<K, V>,
) : Comparable<IntervalPoint<K, V>> {
  val id: K get() = pointData.id
  val sticky get() = pointData.sticky
  val borderKind get() = pointData.borderKind
  val greedy get() = pointData.greedy
  val payload get() = pointData.payload

  override fun compareTo(other: IntervalPoint<K, V>): Int {
    return compareBy<IntervalPoint<K, V>> { it.offset }.thenBy { it.sticky.intValue() }.compare(this, other)
  }

  fun withOffset(offset: Long): IntervalPoint<K, V> {
    return if (offset == this.offset) this else copy(offset = offset)
  }

  fun <L> withId(id: L): IntervalPoint<L, V> {
    return withData(pointData.withId(id))
  }

  private fun <L> withData(data: PointData<L, V>): IntervalPoint<L, V> {
    return IntervalPoint(offset, data)
  }

  fun toNeutralPoint(): IntervalPoint<K, V> {
    return copy(pointData = pointData.toNeutralPoint())
  }

  companion object {
    operator fun invoke(offset: Long, sticky: Sticky?) =
      IntervalPoint(offset, sticky, null)

    operator fun invoke(offset: Long, sticky: Sticky?, borderKind: BorderKind?) =
      IntervalPoint(offset, PointData(Unit, sticky, borderKind, Unit))

    operator fun <K> invoke(id: K, offset: Long, sticky: Sticky?, borderKind: BorderKind?) =
      IntervalPoint(offset, PointData(id, sticky, borderKind, Unit))

    operator fun <K, V> invoke(id: K, offset: Long, sticky: Sticky?, borderKind: BorderKind?, payload: V) =
      IntervalPoint(offset, PointData(id, sticky, borderKind, payload))
  }
}

typealias Point = IntervalPoint<Unit, Unit>

private fun Point.shift(shift: Long): Point {
  return copy(offset = offset + shift)
}

private fun Point.effectiveOffset(direction: () -> Sticky): Long {
  return when (sticky ?: direction()) {
    Sticky.LEFT -> offset - 1
    Sticky.RIGHT -> offset
  }
}

/**
 * Text transformation operates by two main notions:
 * 1. Operations (text edits)
 * 2. Transformable entities that can be transformed by operations by moving their control points
 *
 * Transforming operations is performed as well as for any other transformable entities (by moving control points).
 * So, transformation of operations becomes asymmetric by definition, and all the transformation properties
 * should be considered as the properties of operation being a transformable entity.
 *
 * All the logic of transforming entities is encapsulated into moving control points. It's not guaranteed that
 * the quantity of control points after the transformation will be the same. Of course, there may exist some
 * other modifications of transformable entities (such as inversion for operations).
 *
 * In fact, operation can be considered as transformable entity in two ways: from the both sides.
 * Implementation of one side can be derived from the other using inversion.
 */
fun Sequence<Point>.shiftPoints(operation: Operation, direction: () -> Sticky): Sequence<Long> {
  if (operation.isEmpty) return this.map { 0L }
  val opIterator: Iterator<Op> = operation.ops.iterator()
  var currentDelta = 0L
  var currentCursor = 0L
  fun consumeOperation(): Op {
    val op = opIterator.next()
    when (op) {
      is Op.Retain -> {
        currentCursor += op.len
      }
      is Op.Replace -> {
        currentCursor += op.delete.length
        currentDelta -= op.delete.length
        currentDelta += op.insert.length
      }
    }
    return op
  }

  var currentOp: Op? = null
  return this.map { point ->
    var resultShift: Long? = null
    do {
      if (point.effectiveOffset(direction) >= currentCursor) {
        if (opIterator.hasNext()) {
          currentOp = consumeOperation()
        }
        else {
          require(point.offset == currentCursor && (point.sticky ?: direction()) == Sticky.RIGHT) {
            "Some offsets were not covered by operation: operation = $operation, point = $point"
          }
          resultShift = currentDelta
        }
      }
      else {
        resultShift = when (val op = currentOp) {
          null -> {
            require(point.offset == 0L && (point.sticky ?: direction()) == Sticky.LEFT) {
              "Point before the text: operation = $operation, point = $point"
            }
            0L
          }
          is Op.Retain -> currentDelta
          is Op.Replace -> {
            val rightBorder = currentDelta + currentCursor - point.offset
            when (point.sticky) {
              Sticky.LEFT -> rightBorder - op.insert.length
              Sticky.RIGHT -> rightBorder
              null -> when (point.offset) {
                currentCursor - op.delete.length -> rightBorder - op.insert.length
                currentCursor -> rightBorder
                else -> when (direction()) {
                  Sticky.LEFT -> rightBorder - op.insert.length
                  Sticky.RIGHT -> rightBorder
                }
              }
            }
          }
        }
      }
    }
    while (resultShift == null)
    resultShift
  }
}

private fun <K, V> matchIntervals(starts: MutableList<IntervalPoint<K, V>>,
                                  ends: MutableList<IntervalPoint<K, V>>): List<Pair<IntervalPoint<K, V>, IntervalPoint<K, V>>> {
  val startIds = starts.filter { it.borderKind == BorderKind.START }.associateBy { it.id }
  val endIds = ends.filter { it.borderKind == BorderKind.END }.associateBy { it.id }
  val matchedIds = startIds.keys.intersect(endIds.keys)
  val result = matchedIds.map { requireNotNull(startIds[it]) to requireNotNull(endIds[it]) }
  starts.removeAll { it.id in matchedIds }
  ends.removeAll { it.id in matchedIds }
  return result
}

fun <K, V> shiftPoints2(points: List<IntervalPoint<K, V>>, operation: Operation, neutralDirection: Sticky): List<IntervalPoint<K, V>> {
  if (points.isEmpty()) return points

  fun <K, V> nextPoint(points: List<IntervalPoint<K, V>>, i: Int, shift: Long): IntervalPoint<K, V>? {
    return points.getOrNull(i)?.let { point ->
      point.withOffset(point.offset + shift)
    }
  }

  val result = mutableListOf<IntervalPoint<K, V>>()
  var accumulatedShift = 0L
  var pointIndex = 0
  var point: IntervalPoint<K, V>? = nextPoint(points, pointIndex, accumulatedShift)
  var offset = 0L

  val delayedNeutral = mutableListOf<IntervalPoint<K, V>>()
  val delayedRight = mutableListOf<IntervalPoint<K, V>>()

  for (op in operation.ops) {
    when (op) {
      is Op.Retain -> {
        if (op.len > 0) {
          while (point != null && (point.offset < offset || point.offset == offset && point.sticky == Sticky.LEFT)) {
            result.add(point)
            pointIndex += 1
            point = nextPoint(points, pointIndex, accumulatedShift)
          }
          result.addAll(delayedNeutral.map { it.withOffset(offset) })
          delayedNeutral.clear()
          while (point != null && (point.offset < offset || point.offset == offset && point.sticky != Sticky.RIGHT)) {
            result.add(point)
            pointIndex += 1
            point = nextPoint(points, pointIndex, accumulatedShift)
          }
          result.addAll(delayedRight.map { it.withOffset(offset) })
          delayedRight.clear()
          offset += op.len
        }
      }
      is Op.Replace -> {
        while (point != null && (point.offset < offset || point.offset == offset && point.sticky == Sticky.LEFT)) {
          result.add(point)
          pointIndex += 1
          point = nextPoint(points, pointIndex, accumulatedShift)
        }

        if (neutralDirection == Sticky.LEFT || op.lenBefore > 0) {
          while (point != null && (point.offset < offset || point.offset == offset && point.sticky != Sticky.RIGHT)) {
            delayedNeutral.add(point)
            pointIndex += 1
            point = nextPoint(points, pointIndex, accumulatedShift)
          }
        }
        val offsetAndLenBefore = op.lenBefore + offset
        val leftOwnPoints = mutableListOf<IntervalPoint<K, V>>()
        val neutralOwnPoints = mutableListOf<IntervalPoint<K, V>>()
        val rightOwnPoints = mutableListOf<IntervalPoint<K, V>>()
        while (point != null && (point.offset < offsetAndLenBefore || point.offset == offsetAndLenBefore && point.sticky == Sticky.LEFT)) {
          when (point.sticky) {
            Sticky.LEFT -> leftOwnPoints.add(point)
            Sticky.RIGHT -> rightOwnPoints.add(point)
            null -> neutralOwnPoints.add(point)
          }
          pointIndex += 1
          point = nextPoint(points, pointIndex, accumulatedShift)
        }

        val matchedPrev = matchIntervals(delayedRight, leftOwnPoints)
        val matchedOwn = matchIntervals(rightOwnPoints, leftOwnPoints)
        val (matchedOwnToCurrentStart, matchedOwnToCurrentEnd) = when (neutralDirection) {
          Sticky.RIGHT -> matchedOwn.partition { it.first.offset == offset && it.second.offset != offset + op.lenBefore }
          Sticky.LEFT -> matchedOwn.partition { it.second.offset != offset + op.lenBefore || it.first.offset == offset }
        }

        result.addAll(leftOwnPoints.map { it.withOffset(offset) })
        result.addAll(matchedPrev.map { it.first.toNeutralPoint() }.map { it.withOffset(offset) })
        result.addAll(matchedOwnToCurrentStart.map { it.first.toNeutralPoint() }.map { it.withOffset(offset) })
        if (op.lenAfter > 0) {
          result.addAll(delayedNeutral.map { it.withOffset(offset) })
          delayedNeutral.clear()
        }
        delayedNeutral.addAll(matchedOwnToCurrentEnd.map { it.second.toNeutralPoint() })
        when (neutralDirection) {
          Sticky.LEFT -> {
            result.addAll(neutralOwnPoints.map { it.withOffset(offset) })
          }
          Sticky.RIGHT -> {
            delayedNeutral.addAll(neutralOwnPoints)
          }
        }
        if (op.lenAfter > 0) {
          result.addAll(delayedRight.map { it.withOffset(offset) })
          delayedRight.clear()
        }
        delayedRight.addAll(rightOwnPoints)

        val newShift = op.lenAfter - op.lenBefore
        accumulatedShift += newShift
        point = point?.let { it.withOffset(it.offset + newShift) }

        offset += op.lenAfter
      }
    }
  }

  result.addAll(delayedNeutral.map { it.withOffset(offset) })
  while (point != null && (point.offset < offset || point.offset == offset && point.sticky != Sticky.RIGHT)) {
    result.add(point)
    pointIndex += 1
    point = nextPoint(points, pointIndex, accumulatedShift)
  }
  result.addAll(delayedRight.map { it.withOffset(offset) })
  while (point != null) {
    result.add(point)
    pointIndex += 1
    point = nextPoint(points, pointIndex, accumulatedShift)
  }
  return result
}

/**
 * For N ranges, it creates N local projections of the Operation,
 * each constrained to retain or replace something only within a corresponding range.
 *
 * Important!
 * It doesn't split the Operation in parts that can be later combined back.
 * For example, if the Operation has the only Op.Replace, which replaces three consecutive lines by the whole text of War and Peace,
 * and it is "split" by the ranges of those three lines, then the result will be three Operations,
 * each replacing a single line with a whole text of War and Peace.
 * When combined, three operations would insert the novel three times.
 */
fun Operation.splitEditsByRanges(ranges: List<TextRange>): List<Operation> {
  var rangeIndex = 0
  val opIter = this.ops.iterator()
  var op = if (opIter.hasNext()) opIter.next() else null
  var charOffset = 0L
  val result = ArrayList<Operation>(ranges.size)
  val newOps = ArrayList<Op>()
  while (rangeIndex < ranges.size && op != null) {
    val range = ranges[rangeIndex]
    when (op) {
      is Op.Retain -> {
        val opRange = TextRange(charOffset, charOffset + op.len)

        val (intersection, intersectionType) = opRange.intersect(range)
        if (intersectionType != IntersectionType.None) {
          newOps.add(Op.Retain(intersection.length))
        }
        when (intersectionType) {
          IntersectionType.After, IntersectionType.Outside -> {
            pushOp(result, newOps)
            rangeIndex++
            continue
          }
          else -> {
            if (opRange.start >= range.end) {
              pushOp(result, newOps)
              rangeIndex += 1
              continue
            }
            else {
              charOffset += op.len
              op = if (opIter.hasNext()) opIter.next() else null
            }
          }
        }
      }
      is Op.Replace -> {
        val opRange = TextRange(charOffset, charOffset + op.lenBefore)
        val (intersection, intersectionType) = opRange.intersect(range)
        if (intersectionType != IntersectionType.None) {
          newOps.add(
            Op.Replace(op.delete.substring((intersection.start - charOffset).toInt(), (intersection.end - charOffset).toInt()), op.insert))
        }

        when (intersectionType) {
          IntersectionType.After, IntersectionType.Outside -> {
            pushOp(result, newOps)
            rangeIndex++
            continue
          }
          else -> {
            charOffset += op.lenBefore
            op = if (opIter.hasNext()) opIter.next() else null
          }
        }
      }
    }
  }
  while (rangeIndex < ranges.size) {
    pushOp(result, newOps)
    rangeIndex++
  }
  check(result.size == ranges.size) { "we should build an edit for each range" }
  return result
}

private fun pushOp(
  result: ArrayList<Operation>,
  newOps: ArrayList<Op>,
) {
  result.add(Operation(ArrayList(newOps)))
  newOps.clear()
}