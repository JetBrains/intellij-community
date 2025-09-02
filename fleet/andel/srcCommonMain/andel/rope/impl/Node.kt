// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.rope.impl

import andel.rope.Monoid
import andel.rope.Rope

private const val NODE_SPLIT_THRESHOLD: Int = 32

internal class Node(
  val children: Array<Any?>,
  val metrics: MetricsArray,
)

internal fun <Data> Rope<Data>.zipper(editor: Editor): Zipper<Data> =
  Zipper<Data>(parent = null,
               siblings = arrayOf(root),
               metrics = MetricsArray.of(rootMetrics),
               idx = 0,
               acc = MetricsArray(IntArray(monoid.rank * 7)),
               depth = 0,
               isChanged = false,
               editorDontUse = editor,
               isLeaf = false)

internal fun <T> buildRope(chunks: List<T>, ops: Monoid<T>): Rope<T> {
  require(chunks.isNotEmpty()) {
    "there is no empty ropes, sorry"
  }

  val rank = ops.rank
  val bitMask = ops.bitMask
  var layer: List<Node> = chunks.chunked(NODE_SPLIT_THRESHOLD).map { chunk ->
    Node(children = chunk.toTypedArray(),
         metrics = MetricsArray.builder(rank, chunk.size).apply {
           chunk.forEach { s -> add(ops.measure(s)) }
         }.build())
  }

  while (layer.size > 1) {
    layer = layer.chunked(NODE_SPLIT_THRESHOLD).map { chunk ->
      Node(children = chunk.toTypedArray(),
           metrics = MetricsArray.builder(rank, chunk.size).apply {
             chunk.forEach { n -> add(bitMask.sum(rank, n.metrics)) }
           }.build())
    }
  }
  val root = layer.single()
  return Rope(
    root = root,
    rootMetrics = bitMask.sum(rank, root.metrics),
    monoid = ops
  )
}

internal fun <Data> balanceChildren(ops: Monoid<Data>, node: Node): Node =
  mergeChildren(ops, splitChildren(ops, node))

internal tailrec fun <Data> growTree(ops: Monoid<Data>, node: Node): Node {
  val root = balanceChildren(ops, node)
  return when {
    root.children.size > NODE_SPLIT_THRESHOLD -> {
      val newRoot = Node(arrayOf(root),
                         MetricsArray.of(ops.bitMask.sum(ops.rank, root.metrics)))
      growTree(ops, newRoot)
    }
    else -> root
  }
}

internal tailrec fun shrinkTree(node: Node): Node {
  val children = node.children
  return when {
    children.size == 1 && children[0] is Node ->
      shrinkTree(children[0] as Node)
    else -> node
  }
}

private fun <Data> splitNeeded(children: Array<Any?>, ops: Monoid<Data>): Boolean {
  if (children.isEmpty()) return false
  if (children[0] is Node) {
    for (child in children) {
      if ((child as Node).children.size > NODE_SPLIT_THRESHOLD) {
        return true
      }
    }
  }
  else {
    for (child in children) {
      if (ops.isLeafOverflown(child as Data)) {
        return true
      }
    }
  }
  return false
}

private fun splitNode(rank: Int, result: ArrayList<Node>, source: Node, from: Int, to: Int, thresh: Int) {
  val length = to - from
  if (length <= thresh) {
    result.add(Node(source.children.copyOfRange(from, to),
                    source.metrics.copyOfRange(rank, from, to)))
  }
  else {
    val half = length / 2
    splitNode(rank, result, source, from, from + half, thresh)
    splitNode(rank, result, source, from + half, to, thresh)
  }
}

private fun splitNode(rank: Int, node: Node, threshold: Int): ArrayList<Node> {
  val result = ArrayList<Node>()
  splitNode(rank, result, node, 0, node.children.size, threshold)
  return result
}

/*
 * ensures every child of this node won't be wider than split threshold (still might be underflown),
 * but node itself might become overflown
 * */
private fun <Data> splitChildren(ops: Monoid<Data>, node: Node): Node {
  val metrics = node.metrics
  val children = node.children
  val rank = ops.rank
  if (splitNeeded(children, ops)) {
    val newChildren = ArrayList<Any?>(children.size)
    val newMetrics = MetricsArray.builder(rank, children.size)
    if (children[0] is Node) {
      for (i in children.indices) {
        val child = children[i] as Node
        if (child.children.size > NODE_SPLIT_THRESHOLD) {
          val partition = splitNode(rank, child, NODE_SPLIT_THRESHOLD)
          for (part in partition) {
            newChildren.add(part)
            newMetrics.add(ops.bitMask.sum(ops.rank, part.metrics))
          }
        }
        else {
          newChildren.add(child)
          newMetrics.add(metrics.get(rank, i))
        }
      }
    }
    else {
      for (i in children.indices) {
        val child = children[i] as Data
        if (ops.isLeafOverflown(child)) {
          for (o in ops.split(child)) {
            newChildren.add(o)
            newMetrics.add(ops.measure(o))
          }
        }
        else {
          newChildren.add(child)
          newMetrics.add(metrics.get(rank, i))
        }
      }
    }
    return Node(newChildren.toTypedArray(), newMetrics.build())
  }
  return node
}

private fun <Data> isMergeNeeded(children: Array<Any?>, ops: Monoid<Data>): Boolean =
  when {
    children.isEmpty() -> false
    children[0] is Node -> {
      val mergeThreshold = NODE_SPLIT_THRESHOLD / 2
      children.any { child ->
        (child as Node).children.size < mergeThreshold
      }
    }
    else ->
      children.any { child ->
        ops.isLeafUnderflown(child as Data)
      }
  }

private fun <Data> mergeChildren(ops: Monoid<Data>, node: Node): Node {
  if (isMergeNeeded(node.children, ops)) {
    val mergeThreshold = NODE_SPLIT_THRESHOLD / 2
    val rank = ops.rank
    val newChildren = ArrayList<Any?>(mergeThreshold)
    val newMetrics = MetricsArray.builder(rank, mergeThreshold)
    if (node.children[0] is Node) {
      var left = node.children[0] as Node
      var leftMetrics = node.metrics.get(rank, 0)
      for (i in 1 until node.children.size) {
        val right = node.children[i] as Node
        val rightMetrics = node.metrics.get(rank, i)
        if (left.children.size < mergeThreshold || right.children.size < mergeThreshold) {
          if (left.children.size + right.children.size > NODE_SPLIT_THRESHOLD) {
            val N = left.children.size + right.children.size
            val halfN = N / 2
            val leftCesure = minOf(halfN, left.children.size)
            val rightCesure = maxOf(0, halfN - left.children.size)

            val left2 = Node(
              children = concatArrays(left.children, 0, leftCesure,
                                      right.children, 0, rightCesure),
              metrics = concatArrays(rank,
                                     left.metrics, 0, leftCesure,
                                     right.metrics, 0, rightCesure)
            )

            val right2 = Node(
              children = concatArrays(left.children, leftCesure, left.children.size,
                                      right.children, rightCesure, right.children.size),
              metrics = concatArrays(rank,
                                     left.metrics, leftCesure, left.children.size,
                                     right.metrics, rightCesure, right.children.size)
            )

            val mergedLeft = mergeChildren(ops, left2)
            newChildren.add(mergedLeft)
            newMetrics.add(ops.bitMask.sum(ops.rank, mergedLeft.metrics))
            left = right2
            leftMetrics = ops.bitMask.sum(ops.rank, right2.metrics)
          }
          else {
            left = mergeChildren(ops, Node(concatArrays(left.children, right.children),
                                           concatArrays(left.metrics, right.metrics)))
            leftMetrics = ops.bitMask.sum(ops.rank, left.metrics)
          }
        }
        else {
          newChildren.add(left)
          newMetrics.add(leftMetrics)
          left = right
          leftMetrics = rightMetrics
        }
      }
      newChildren.add(left)
      newMetrics.add(leftMetrics)
    }
    else {
      var leftData = node.children[0] as Data
      var leftMetrics = node.metrics.get(rank, 0)
      for (i in 1 until node.children.size) {
        val rightData = node.children[i] as Data
        val rightMetrics = node.metrics.get(rank, i)
        if (ops.isLeafUnderflown(leftData) || ops.isLeafUnderflown(rightData)) {
          val mergedData = ops.merge(leftData, rightData)
          if (ops.isLeafOverflown(mergedData)) {
            val split = ops.split(mergedData)
            val dataLeft = split[0]
            val dataRight = split[1]
            check(split.size <= 2) { "split(merge(leaf1, leaf2)) should be of size <= 2" }
            newChildren.add(dataLeft)
            newMetrics.add(ops.measure(dataLeft))
            leftData = dataRight
            leftMetrics = ops.measure(dataRight)
          }
          else {
            leftData = mergedData
            leftMetrics = ops.measure(mergedData)
          }
        }
        else {
          newChildren.add(leftData)
          newMetrics.add(leftMetrics)
          leftData = rightData
          leftMetrics = rightMetrics
        }
      }
      newChildren.add(leftData)
      newMetrics.add(leftMetrics)
    }
    return Node(newChildren.toTypedArray(), newMetrics.build())
  }
  return node
}

internal fun concatArrays(
  left: Array<Any?>, leftFrom: Int, leftTo: Int,
  right: Array<Any?>, rightFrom: Int, rightTo: Int,
): Array<Any?> =
  arrayOfNulls<Any?>(leftTo - leftFrom + rightTo - rightFrom).also {
    left.copyInto(it, 0, leftFrom, leftTo)
    right.copyInto(it, leftTo - leftFrom, rightFrom, rightTo)
  }

internal fun concatArrays(
  left: IntArray, leftFrom: Int, leftTo: Int,
  right: IntArray, rightFrom: Int, rightTo: Int,
): IntArray =
  IntArray(leftTo - leftFrom + rightTo - rightFrom).also {
    left.copyInto(it, 0, leftFrom, leftTo)
    right.copyInto(it, leftTo - leftFrom, rightFrom, rightTo)
  }


internal fun <T> concatArrays(left: Array<T>, right: Array<T>): Array<T> = left + right
