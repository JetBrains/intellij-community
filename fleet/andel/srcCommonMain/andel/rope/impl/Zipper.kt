// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.rope.impl

import andel.rope.*

internal typealias Editor = Any?

internal class Zipper<Data> internal constructor(
  internal var editorDontUse: Editor,
  internal val parent: Zipper<Data>?,
  internal var siblings: Array<Any?>,
  internal var metrics: MetricsArray,
  internal val depth: Int,
  internal val acc: MetricsArray,
  internal var idx: Int = 0,
  internal var isChanged: Boolean,
  internal val isLeaf: Boolean,
) {
  private fun mutableCopy(editor: Editor): Zipper<Data> =
    when {
      editor === editorDontUse -> this
      else -> Zipper(editorDontUse = editor,
                     parent = parent,
                     siblings = siblings,
                     metrics = metrics,
                     idx = idx,
                     depth = depth,
                     acc = acc.copy(),
                     isChanged = isChanged,
                     isLeaf = isLeaf)
    }

  internal fun node(): Node =
    siblings[idx] as Node

  @Suppress("UNCHECKED_CAST")
  internal fun data(): Data {
    require(isLeaf) {
      "not leaf"
    }
    return siblings[idx] as Data
  }

  internal fun location(rank: Int, kind: Metric): Int =
    acc.get(rank, depth, kind)

  internal fun isBranch(): Boolean =
    !isLeaf

  internal fun size(rank: Int): Metrics =
    metrics.get(rank, idx)

  internal fun size(rank: Int, kind: Metric): Int =
    metrics.get(rank, idx, kind)

  internal fun isRoot(): Boolean =
    parent == null

  internal fun isRightmost(): Boolean =
    idx == siblings.size - 1 && (parent == null || parent.isRightmost())

  internal fun isLeftmost(): Boolean =
    idx == 0 && (parent == null || parent.isLeftmost())

  internal fun replace(editor: Editor, ops: Monoid<Data>, node: Any?, nodeMetrics: Metrics): Zipper<Data> =
    mutableCopy(editor).also { mut ->
      val siblings =
        if (mut.isChanged) mut.siblings
        else mut.siblings.copyOf()
      val metrics =
        if (mut.isChanged) metrics
        else mut.metrics.copy()
      siblings[idx] = node
      metrics.set(ops.rank, idx, nodeMetrics)
      mut.isChanged = true
      mut.siblings = siblings
      mut.metrics = metrics
    }

  internal fun up(editor: Editor, ops: Monoid<Data>): Zipper<Data>? =
    when {
      isChanged && parent == null -> {
        val node = shrinkTree(growTree(ops, node()))
        Zipper(idx = 0,
               depth = 0,
               acc = acc,
               isChanged = false,
               siblings = arrayOf(node),
               metrics = MetricsArray.of(ops.bitMask.sum(ops.rank, node.metrics)),
               editorDontUse = editor,
               parent = null,
               isLeaf = false)
      }
      isChanged && parent != null -> {
        val balanced = balanceChildren(ops, Node(siblings, metrics))
        parent.replace(editor, ops, balanced, ops.bitMask.sum(ops.rank, balanced.metrics))
      }
      else -> {
        parent
      }
    }

  internal fun hasRight(): Boolean = idx < siblings.size - 1

  /*
  * moves to next direct sibling and adds metrics of current node to accumulator
  * returns null if it is last child
  * */
  internal fun right(editor: Editor, bitMask: BitMask, rank: Int): Zipper<Data>? =
    when {
      idx < siblings.size - 1 ->
        mutableCopy(editor).also { mut ->
          bitMask.sum(rank,
                      lhs = mut.metrics, lhsFrom = idx,
                      dest = mut.acc, destIndex = mut.depth)
          mut.idx = idx + 1
        }
      else -> null
    }

  /*
  * moves to first child of current node
  * returns null for leaves and empty nodes
  * */
  internal fun downLeft(rank: Int, editor: Editor): Zipper<Data>? =
    when {
      isBranch() -> {
        val n = node()
        when {
          n.children.isEmpty() -> null
          else ->
            Zipper(
              parent = this,
              siblings = n.children,
              metrics = n.metrics,
              idx = 0,
              acc = (if (editor === editorDontUse) acc else acc.copy()).also { acc ->
                acc.set(rank, depth + 1, acc, depth)
              },
              depth = depth + 1,
              isChanged = false,
              editorDontUse = editor,
              isLeaf = n.children[0] !is Node
            )
        }
      }
      else -> null
    }

  /*
  * moves to last child of current node
  * returns null for leaves and empty nodes
  *
  * CAUTION: drops accumulated position
  * */
  internal fun downRight(editor: Editor, bitMask: BitMask, rank: Int): Zipper<Data>? =
    when {
      isBranch() -> {
        val n = node()
        when {
          n.children.isEmpty() -> null
          else -> Zipper(
            parent = this,
            siblings = n.children,
            metrics = n.metrics,
            idx = n.children.size - 1,
            acc = (if (editor === editorDontUse) acc else acc.copy()).also { acc ->
              acc.set(rank, depth + 1, acc, depth)
              bitMask.sum(
                rank = rank,
                dest = acc, destIndex = depth + 1,
                lhs = n.metrics, lhsFrom = 0, lhsTo = n.children.size - 1,
              )
            },
            depth = depth + 1,
            isChanged = false,
            editorDontUse = editor,
            isLeaf = n.children[0] !is Node
          )
        }
      }
      else -> null
    }

  internal fun rope(editor: Editor, ops: Monoid<Data>): Rope<Data> =
    when (val parent = up(editor, ops)) {
      null -> Rope(node(), size(ops.rank), ops)
      else -> parent.rope(editor, ops)
    }

  internal fun hasNext(): Boolean =
    when {
      isBranch() -> node().children.size > 0
      else -> !isRightmost()
    }

  internal fun hasPrev(): Boolean =
    when {
      isBranch() -> parent != null
      else -> !isLeftmost()
    }

  internal fun next(editor: Editor, ops: Monoid<Data>): Zipper<Data>? =
    when {
      isBranch() -> downLeft(ops.rank, editor)
      else -> skip(editor, ops)
    }

  // drops acc!!!
  internal fun left(editor: Editor, ops: Monoid<Data>): Zipper<Data>? =
    when {
      0 < idx ->
        mutableCopy(editor).also { mut ->
          mut.idx = idx - 1
        }
      else -> null
    }

  internal fun prev(editor: Editor, ops: Monoid<Data>): Zipper<Data>? =
    when {
      isBranch() -> downRight(editor, ops.bitMask, ops.rank)
      else -> leftup(editor, ops)
    }

  internal fun downToLeaf(editor: Editor, ops: Monoid<Data>): Zipper<Data>? =
    when {
      isLeaf -> this
      else -> nextLeaf(editor, ops)
    }
}

internal tailrec fun <Data> Zipper<Data>.leftup(editor: Editor, ops: Monoid<Data>): Zipper<Data>? {
  val left = left(editor, ops)
  return if (left != null) left else up(editor, ops)?.leftup(editor, ops)
}

internal tailrec fun <Data> Zipper<Data>.prevLeaf(editor: Editor, ops: Monoid<Data>): Zipper<Data>? {
  val prev = prev(editor, ops)
  return when {
    prev == null -> null
    prev.isBranch() -> prev.prevLeaf(editor, ops)
    else -> prev
  }
}

internal tailrec fun <Data> Zipper<Data>.nextLeaf(editor: Editor, ops: Monoid<Data>): Zipper<Data>? {
  val n = next(editor, ops)
  return when {
    n == null -> null
    n.isBranch() -> n.nextLeaf(editor, ops)
    else -> n
  }
}

internal tailrec fun <Data> Zipper<Data>.skip(editor: Editor, ops: Monoid<Data>): Zipper<Data>? {
  val right = right(editor, ops.bitMask, ops.rank)
  return if (right != null) right else up(editor, ops)?.skip(editor, ops)
}

internal tailrec fun <Data> Zipper<Data>.lastLeaf(editor: Editor, ops: Monoid<Data>): Zipper<Data>? =
  if (isBranch()) {
    val downRight = downRight(editor, ops.bitMask, ops.rank)
    if (downRight != null) downRight.lastLeaf(editor, ops) else null
  }
  else {
    this
  }

/*
* stops in a data node satisfying predicate or in a root node if the tree is empty
* will return null if scan to the right found no value satisfying the predicate
*/
internal tailrec fun <Data> Zipper<Data>.scan(editor: Editor, ops: Monoid<Data>, offset: Int, kind: Metric): Zipper<Data>? {
  val rank = ops.rank
  val mask = ops.bitMask
  val loc = acc.get(rank, depth, kind)
  return when {
    // fast path
    isLeaf && offset < mask.sum(kind, loc, metrics.get(rank, idx, kind)) ->
      this
    else -> {
      var found: Boolean = false
      var z: Zipper<Data> = this
      while (true) {
        if (offset < mask.sum(kind, z.acc.get(rank, depth, kind), z.metrics.get(rank, z.idx, kind))) {
          found = true
          break
        }
        else {
          if (z.hasRight()) {
            z = z.right(editor, mask, rank)!!
          }
          else {
            break
          }
        }
      }
      if (found) {
        if (z.isBranch()) {
          z.downLeft(rank, editor)!!.scan(editor, ops, offset, kind)
        }
        else {
          z
        }
      }
      else {
        if (z.isRightmost()) {
          z.lastLeaf(editor, ops)
        }
        else {
          z.skip(editor, ops)!!.scan(editor, ops, offset, kind)
        }
      }
    }
  }
}
