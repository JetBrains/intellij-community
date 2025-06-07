// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.operation.NewOffsetProvider
import andel.operation.Op
import andel.operation.Operation
import fleet.util.logging.KLoggers
import fleet.util.tree.Node
import fleet.util.tree.PushFun
import fleet.util.tree.Treap
import fleet.util.tree.UpdateFun

private val logger by lazy { KLoggers.logger(OpTreapData::class) }

internal typealias OpTreap = Treap<OpTreapData>

private val updateF: UpdateFun<OpTreapData> = { thisData, leftData, rightData ->
  val max = maxOf(thisData.delValue, leftData.effectiveMax, rightData.effectiveMax)
  thisData.copy(
    max = max,
    numMax = (if (thisData.delValue == max) thisData.size else 0) +
             (if (leftData?.effectiveMax == max) leftData.numMax else 0) +
             (if (rightData?.effectiveMax == max) rightData.numMax else 0),
    sum = thisData.addValue + leftData.effectiveSum + rightData.effectiveSum,
  )
}

private val pushF: PushFun<OpTreapData> = { thisData, leftData, rightData ->
  Triple(
    thisData.copy(delValue = thisData.delValue + thisData.deltaMax,
                  max = thisData.max + thisData.deltaMax,
                  deltaMax = 0),
    leftData?.copy(deltaMax = leftData.deltaMax + thisData.deltaMax),
    rightData?.copy(deltaMax = rightData.deltaMax + thisData.deltaMax)
  )
}

/**
 * Builds a treap holding a collection of operations, allowing rebasing offsets on all these operations in logarithmic time.
 *
 * See [getNewOffset]
 */
internal fun buildOperationTreap(operations: List<Operation>): Treap<OpTreapData> {
  val allX = LinkedHashSet<Long>(operations.size * operations.first().size)
  allX.add(0)
  for (o in operations) {
    var offset = 0L
    for (op in o.ops) {
      when (op) {
        is Op.Retain -> offset += op.len
        is Op.Replace -> offset += op.delete.length
      }
      allX.add(offset)
    }
  }
  val xs = allX.toMutableList().apply { sort() }

  val treapData = (0 until xs.size).map { idx ->
    OpTreapData.empty(xs[idx], xs.getOrElse(idx + 1) { xs.last() + 1 } - xs[idx])
  }

  val treap = Treap.build(treapData, xs, updateF, pushF)
  for (o in operations) {
    treap.addRemoveOperation(o, isAdd = true)
  }

  logger.debug { "Building operation treap.\n  Operations=$operations\n  Treap=$treap" }
  return treap
}

internal fun OpTreap.addRemoveOperation(o: Operation, isAdd: Boolean) {
  logger.debug { (if (isAdd) "Add" else "Remove") + " operation $o" }
  var offset = 0L
  for (op in o.ops) {
    when (op) {
      is Op.Retain -> offset += op.len
      is Op.Replace -> {
        if (op.delete.isNotEmpty()) {
          addRemoveDeletion(offset, offset + op.delete.length, isAdd = isAdd)
        }
        if (op.insert.isNotEmpty()) {
          val length = op.insert.length.toLong()
          addAtOffset(offset, if (isAdd) length else -length)
        }
        offset += op.delete.length
      }
    }
  }
}

internal fun OpTreap.addAtOffset(offset: Long, length: Long) {
  change(offset) { data ->
    data.copy(addValue = data.addValue + length, // just because it's a single node we don't push this stat
              sum = data.sum + length)
  }
}

internal fun OpTreap.addRemoveDeletion(start: Long, end: Long, isAdd: Boolean) {
  change(start, end) { data ->
    data.copy(deltaMax = data.deltaMax + (if (isAdd) -1 else 1))
  }
}

internal fun OpTreap.getNewOffset(offset: Long): Long {
  var result: Long = offset
  withSplit(0, offset) { root ->
    if (root != null) {
      val data = root.data
      result = data.sum + (if (data.effectiveMax == 0) data.numMax else 0)

      var rightMostNode: Node<OpTreapData> = root
      while (rightMostNode.right != null) rightMostNode = rightMostNode.right!!
      require(rightMostNode.x < offset)
      if (rightMostNode.data.delValue + rightMostNode.data.deltaMax == 0) {
        result -= rightMostNode.x + rightMostNode.data.size - offset
      }
    }
  }
  logger.debug { "Query new offset $offset -> $result" }
  return result
}

internal fun OpTreap.asNewOffsetProvider(): NewOffsetProvider = NewOffsetProvider { offset ->
  getNewOffset(offset)
}

// size: offset size, i.e. length till next offset.
// delValue - number of times this offset..offset+size is in deleted op. addValue - number of added characters at this offset
// max - max value; numMax - count of max value elements; deltaMax: change in max stat. sum - sum of values, deltaSum - change in sum stat.
internal data class OpTreapData(val size: Long,
                                val delValue: Int,
                                val max: Int,
                                val numMax: Long,
                                val deltaMax: Int,
                                val addValue: Long,
                                val sum: Long) {
  companion object {
    fun empty(x: Long, size: Long) = OpTreapData(size = size,
                                                 delValue = 0,
                                                 max = 0,
                                                 numMax = size,
                                                 deltaMax = 0,
                                                 addValue = 0,
                                                 sum = 0)
  }
}

private val OpTreapData?.effectiveMax: Int get() = if (this == null) Int.MIN_VALUE else max + deltaMax
private val OpTreapData?.effectiveSum: Long get() = this?.sum ?: 0L