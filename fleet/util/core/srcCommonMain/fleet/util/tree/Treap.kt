// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.tree

import kotlin.random.Random

typealias UpdateFun<T> = (T, T?, T?) -> T

typealias PushFun<T> = (T, T?, T?) -> Triple<T, T?, T?>

/**
 * Mutable Treap. Not thread safe.
 * Unlike [java.util.TreeMap] Treap supports custom node statistics, see [Node.update] and [Node.push].
 *
 * lesser x are to the left.
 * lesser y are closer to root.
 *
 * y are selected randomly which provides O(log N) operations on average.
 */
class Treap<T> private constructor(private var root: Node<T>?, private val updateF: UpdateFun<T>, private val pushF: PushFun<T>) {
  companion object {
    fun <T> build(values: List<T>, xs: List<Long>, updateF: UpdateFun<T>, pushF: PushFun<T>): Treap<T> {
      require(values.size == xs.size)
      require(xs.sorted() == xs)
      val n = values.size
      require(n > 0)

      val ys = ArrayList<Int>(values.size).apply {
        repeat(n) { add(Random.nextInt()) }
      }

      val rightShore = ArrayDeque<Node<T>>()
      for (i in 0 until n) {
        val myY = ys[i]
        val newNode = Node(values[i], xs[i], myY, null, null)
        var lastNode: Node<T>? = null
        while (rightShore.isNotEmpty() && rightShore.last().y > myY) {
          lastNode = rightShore.removeLast()
        }
        if (lastNode != null) {
          newNode.left = lastNode
          newNode.update(updateF)
        }
        if (rightShore.isNotEmpty()) {
          rightShore.last().right = newNode
          rightShore.last().update(updateF)
        }
        rightShore.add(newNode)
      }
      return Treap(rightShore.first(), updateF, pushF)
    }
  }

  /**
   * Returns data on interval [l, r)
   */
  fun query(start: Long, end: Long): T? {
    return change(start, end) { it }
  }

  /**
   * [start, end)
   */
  fun change(start: Long, end: Long, changeF: (T) -> T): T? {
    val (l, m1) = split(root, start)
    val (m2, r) = split(m1, end)
    val result = m2?.run { data = changeF(data); data }
    root = merge(l, merge(m2, r))
    return result
  }

  fun withSplit(start: Long, end: Long, introspection: (Node<T>?) -> Unit) {
    val (l, m1) = split(root, start)
    val (m2, r) = split(m1, end)
    introspection(m2)
    root = merge(l, merge(m2, r))
  }

  fun change(offset: Long, changeF: (T) -> T): T? {
    return change(offset, offset + 1, changeF)
  }

  /**
   * Mutates the tree. Be careful.
   *
   * @return tree with xs < [x] and tree with xs >= [x]
   */
  private fun split(root: Node<T>?, x: Long): Pair<Node<T>?, Node<T>?> {
    if (root == null) return null to null

    root.push(pushF)
    if (root.x >= x) {
      val (sLeft, sRight) = split(root.left, x)
      root.left = sRight
      root.update(updateF)
      return sLeft to root
    }
    else {
      val (sLeft, sRight) = split(root.right, x)
      root.right = sLeft
      root.update(updateF)
      return root to sRight
    }
  }

  /**
   * meaning: root1.max { x } < root2.min { x }
   *
   * Mutates the tree. Be careful.
   */
  private fun merge(root1: Node<T>?, root2: Node<T>?): Node<T>? {
    if (root1 == null) return root2
    if (root2 == null) return root1
    if (root1.y <= root2.y) {
      root1.push(pushF)
      root1.right = merge(root1.right, root2)
      root1.update(updateF)
      return root1
    }
    else {
      root2.push(pushF)
      root2.left = merge(root1, root2.left)
      root2.update(updateF)
      return root2
    }
  }

  override fun toString(): String {
    return "Treap<${root}>"
  }
}


///**
// * Not thread safe.
// *
// * lesser y are closer to root.
// */
//class ImplicitKeyTreap<T> private constructor(private var root: ImplicitNode<T>?, private val f: UpdateFun<T>) {
//  /**
//   * Mutates the tree. Be careful.
//   */
//  private fun split(root: ImplicitNode<T>?, nodeCount: Int): Pair<ImplicitNode<T>?, ImplicitNode<T>?> {
//    if (root == null) return null to null
//
//    val leftNode = root.left
//    val leftCount = leftNode?.size ?: 0
//    if (leftCount >= nodeCount) {
//      val (sLeft, sRight) = split(leftNode, nodeCount)
//      root.left = sRight
//      root.recalcStats(f)
//      return sLeft to root
//    }
//    else {
//      val (sLeft, sRight) = split(root.right, nodeCount - 1 - leftCount)
//      root.right = sLeft
//      root.recalcStats(f)
//      return root to sRight
//    }
//  }
//
//  /**
//   * meaning: root1.x < root2.x
//   *
//   * Mutates the tree. Be careful.
//   */
//  private fun merge(root1: ImplicitNode<T>?, root2: ImplicitNode<T>?): ImplicitNode<T>? {
//    if (root1 == null) return root2
//    if (root2 == null) return root1
//    if (root1.y <= root2.y) {
//      root1.right = merge(root1.right, root2)
//      root1.recalcStats(f)
//      return root1
//    }
//    else {
//      root2.left = merge(root1, root2.left)
//      root2.recalcStats(f)
//      return root2
//    }
//  }
//}

data class Node<T>(var data: T, var x: Long, val y: Int, var left: Node<T>?, var right: Node<T>?) {
  fun update(f: UpdateFun<T>) {
    data = f(data, left?.data, right?.data)
  }

  fun push(f: PushFun<T>) {
    val (newData, newLeftData, newRightData) = f(data, left?.data, right?.data)
    data = newData
    left?.run { data = newLeftData!! }
    right?.run { data = newRightData!! }
  }
}

//private data class ImplicitNode<T>(val data: T, var stats: S, var size: Int, val y: Int, var left: ImplicitNode<T>?, var right: ImplicitNode<T>?) {
//  fun recalcStats(f: UpdateFun<T>) {
//    size = 1 + (left?.size ?: 0) + (right?.size ?: 0)
//    stats = f(data, left?.stats, right?.stats)
//  }
//}