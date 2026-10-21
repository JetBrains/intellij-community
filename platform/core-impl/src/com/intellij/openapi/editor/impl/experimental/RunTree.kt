// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

/**
 * A persistent, append-only sequence of runs, searched by an ascending int key. The run tree of a
 * graph keys a run by its lvStart. The tree of one agent keys it as [AgentIndex] describes.
 *
 * The layout is a 32-way B+tree with a tail, as in Clojure's vector. Every leaf in the tree is
 * full, and the newest runs wait in the tail until [WIDTH] of them fill a leaf. Every node keeps an
 * [IntArray] with the first key of each child. So a search by key descends on primitive arrays,
 * and it loads no run before the leaf. A binary search over a flat array of runs instead loads a
 * run at every probe, and that measured slower.
 *
 * Every node except the ones on the rightmost path is full. So the index of a run follows from its
 * path by arithmetic, and a search by index needs no count per node.
 *
 * An append copies the tail, and every [WIDTH]th append also copies the rightmost path. The old
 * tree stays valid and shares every other node. The tree is immutable, and every field is final.
 */
internal class RunTree private constructor(
  private val root: Node?,
  private val height: Int, // The inner levels above the leaves. 0 means the root is a leaf
  private val leafCount: Int,
  private val tailKeys: IntArray,
  private val tailRuns: Array<StoredRun>,
) {

  /**
   * The number of runs.
   */
  fun size(): Int {
    return leafCount * WIDTH + tailRuns.size
  }

  /**
   * The newest run, or `null` in an empty tree. The tail is empty only in an empty tree.
   */
  fun last(): StoredRun? {
    return if (tailRuns.isEmpty()) null else tailRuns[tailRuns.size - 1]
  }

  /**
   * This tree with [run] appended under [key]. The key must exceed every key the tree holds.
   */
  fun appended(key: Int, run: StoredRun): RunTree {
    checkAscending(key)
    if (tailRuns.size < WIDTH) {
      return RunTree(root, height, leafCount, tailKeys + key, tailRuns + run)
    }
    val leaf = Leaf(tailKeys, tailRuns)
    val newRoot: Node
    val newHeight: Int
    if (root == null) {
      newRoot = leaf
      newHeight = 0
    } else if (leafCount == capacity(height)) {
      // The tree is full: a new root holds the old one and a path down to the new leaf.
      newRoot = Inner(intArrayOf(root.keys[0], leaf.keys[0]), arrayOf(root, pathTo(leaf, height)))
      newHeight = height + 1
    } else {
      // A root of height 0 is a leaf only while it is full, and a full tree took the branch above.
      newRoot = pushed(root as Inner, height, leaf, leafCount)
      newHeight = height
    }
    return RunTree(newRoot, newHeight, leafCount + 1, intArrayOf(key), arrayOf(run))
  }

  /**
   * The run with the greatest key at or below [key], or `null` when every key exceeds it.
   */
  fun floor(key: Int): StoredRun? {
    if (tailKeys.isNotEmpty() && key >= tailKeys[0]) {
      return tailRuns[floorIn(tailKeys, key)]
    }
    var node = root ?: return null
    while (node is Inner) {
      val child = floorIn(node.keys, key)
      if (child < 0) {
        return null
      }
      node = node.children[child]
    }
    val leaf = node as Leaf
    val index = floorIn(leaf.keys, key)
    return if (index < 0) null else leaf.runs[index]
  }

  /**
   * The index of the run that [floor] finds, or -1.
   */
  fun floorIndex(key: Int): Int {
    if (tailKeys.isNotEmpty() && key >= tailKeys[0]) {
      return leafCount * WIDTH + floorIn(tailKeys, key)
    }
    var node = root ?: return -1
    var leafIndex = 0
    var level = height
    while (node is Inner) {
      val child = floorIn(node.keys, key)
      if (child < 0) {
        return -1
      }
      // Every child before this one is a full subtree.
      leafIndex += child * capacity(level - 1)
      node = node.children[child]
      level--
    }
    val leaf = node as Leaf
    val index = floorIn(leaf.keys, key)
    return if (index < 0) -1 else leafIndex * WIDTH + index
  }

  /**
   * The run at [index], which counts runs from 0.
   */
  fun get(index: Int): StoredRun {
    checkIndex(index)
    val treeRuns = leafCount * WIDTH
    if (index >= treeRuns) {
      return tailRuns[index - treeRuns]
    }
    var node = requireRoot()
    var leafIndex = index / WIDTH
    var level = height
    while (node is Inner) {
      val childCapacity = capacity(level - 1)
      node = node.children[leafIndex / childCapacity]
      leafIndex %= childCapacity
      level--
    }
    return (node as Leaf).runs[index % WIDTH]
  }

  /**
   * [node] with [leaf] added as its rightmost leaf. [leavesBefore] counts the leaves under [node],
   * and [level] is its height. A full rightmost child gets a new sibling; any other gets the leaf.
   */
  private fun pushed(
    node: Inner,
    level: Int,
    leaf: Leaf,
    leavesBefore: Int,
  ): Inner {
    val childCapacity = capacity(level - 1)
    if (leavesBefore % childCapacity == 0) {
      return Inner(node.keys + leaf.keys[0], node.children + pathTo(leaf, level - 1))
    }
    val last = node.children.size - 1
    val children = node.children.copyOf()
    // The branch above takes the case where the child at the level 1 is a full leaf, so this child
    // is an inner node.
    children[last] = pushed(node.children[last] as Inner, level - 1, leaf, leavesBefore % childCapacity)
    return Inner(node.keys, children)
  }

  /**
   * [leaf] under [levels] inner nodes of one child each.
   */
  private fun pathTo(leaf: Leaf, levels: Int): Node {
    var node: Node = leaf
    repeat(levels) {
      node = Inner(intArrayOf(leaf.keys[0]), arrayOf(node))
    }
    return node
  }

  /**
   * The root, for a caller that holds the index of a run in a full leaf. Leaves enter the tree
   * only when they are full, so such a run exists only when the root does.
   */
  private fun requireRoot(): Node {
    val root = root
    require(root != null) {
      "A tree of ${size()} runs has no root, but it holds $leafCount full leaves"
    }
    return root
  }

  private fun checkAscending(key: Int) {
    require(tailKeys.isEmpty() || key > tailKeys[tailKeys.size - 1]) {
      "The key $key does not exceed the last key ${tailKeys[tailKeys.size - 1]}"
    }
  }

  private fun checkIndex(index: Int) {
    require(index in 0 until size()) {
      "The run index $index is out of a tree of ${size()} runs"
    }
  }

  override fun toString(): String {
    return "RunTree(runs=${size()}, height=$height)"
  }

  /**
   * A node, with the first key of every child or run it holds, ascending.
   */
  private sealed class Node(@JvmField val keys: IntArray)

  private class Leaf(keys: IntArray, @JvmField val runs: Array<StoredRun>) : Node(keys)

  private class Inner(keys: IntArray, @JvmField val children: Array<Node>) : Node(keys)

  companion object {
    private const val SHIFT = 5

    /**
     * The runs of a leaf, and the children of an inner node.
     */
    const val WIDTH: Int = 1 shl SHIFT

    val EMPTY: RunTree = RunTree(null, 0, 0, IntArray(0), emptyArray())

    /**
     * The leaves under a full subtree of [height] inner levels.
     */
    private fun capacity(height: Int): Int {
      return 1 shl (SHIFT * height)
    }

    /**
     * The index of the greatest key at or below [key], or -1 when every key exceeds it.
     */
    private fun floorIn(keys: IntArray, key: Int): Int {
      var lo = 0
      var hi = keys.size - 1
      var found = -1
      while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (keys[mid] <= key) {
          found = mid
          lo = mid + 1
        } else {
          hi = mid - 1
        }
      }
      return found
    }
  }
}
