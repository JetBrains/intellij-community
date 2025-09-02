// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals.impl

import andel.intervals.Interval
import andel.intervals.IntervalsIterator
import fleet.fastutil.longs.LongArrayList
import fleet.fastutil.longs.indexOf
import fleet.fastutil.longs.indices
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
object Impl {
  private const val OPEN_ROOT_ID: Long = -1
  private const val CLOSED_ROOT_ID: Long = -2
  private const val FIRST_ID: Long = -3
  const val MAX_VALUE = Long.MAX_VALUE / 2 - 1

  /*
   * we employ two separate trees to store markers with greedy and not greedy start
   * this is necessary because we store intervals sorted by start offset
   * this order is hard to preserve in situations of open/closed intervals starting on the same offset
   * e.g. (0, 5) [1 3] -> collapse(0, 2) -> (0, 3) [0, 1] -> expand(0, 1) -> (1, 4) [0, 2] !!!
   * */
  private val EMPTY_DROPPING: IntervalsImpl<*> = emptyImpl<Any>(32, true)
  private val EMPTY_KEEPING: IntervalsImpl<*> = emptyImpl<Any>(32, false)

  @JvmStatic
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> empty(dropEmpty: Boolean): IntervalsImpl<T> {
    return if (dropEmpty) {
      EMPTY_DROPPING as IntervalsImpl<T>
    }
    else {
      EMPTY_KEEPING as IntervalsImpl<T>
    }
  }

  @JvmStatic
  fun <T : Any> emptyImpl(maxChildren: Int, dropEmpty: Boolean): IntervalsImpl<T> {
    val openRoot = Node.empty(maxChildren / 2)
    val closedRoot = Node.empty(maxChildren / 2)
    return IntervalsImpl(maxChildren, openRoot, closedRoot, persistentHashMapOf<Long, Long>(), FIRST_ID, dropEmpty)
  }

  private fun splitNode(result: ArrayList<Node>, source: Node, from: Int, to: Int, thresh: Int) {
    val length = to - from
    if (length <= thresh) {
      val children = ArrayList<Any>(to - from)
      children.addAll(source.children.subList(from, to))
      result.add(Node(LongArrayList(source.ids.elements(), from, to - from),
                      LongArrayList(source.starts.elements(), from, to - from),
                      LongArrayList(source.ends.elements(), from, to - from),
                      children))
    }
    else {
      val half = length / 2
      splitNode(result, source, from, from + half, thresh)
      splitNode(result, source, from + half, to, thresh)
    }
  }

  private fun splitNode(node: Node, splitThreshold: Int): ArrayList<Node> {
    val result = ArrayList<Node>()
    splitNode(result, node, 0, node.children.size, splitThreshold)
    return result
  }

  private fun childrenNeedSplitting(node: Node, splitThreshold: Int): Boolean {
    for (child in node.children) {
      if (child is Node) {
        if (child.children.size > splitThreshold) {
          return true
        }
      }
      else {
        return false
      }
    }
    return false
  }

  private fun adopt(parentsMap: PersistentMap.Builder<Long, Long>, parentId: Long, childrenIds: LongArrayList?): PersistentMap.Builder<Long, Long> {
    var parentsMap = parentsMap
    for (k in childrenIds!!.indices) {
      parentsMap.put(childrenIds.get(k), parentId)
    }
    return parentsMap
  }

  private fun splitChildren(ctx: EditingContext?, node: Node): Node {
    val splitThreshold = ctx!!.maxChildren
    return if (childrenNeedSplitting(node, splitThreshold)) {
      val result = Node.empty(splitThreshold / 2)
      var m = ctx.parentsMap
      var nextId = ctx.nextId
      for (i in node.children.indices) {
        val child = node.children[i] as Node
        val childDelta = node.starts[i]
        val childId = node.ids[i]
        val parentId = m[childId]
        if (child.children.size > splitThreshold) {
          val partition = splitNode(child, splitThreshold)
          for (j in partition.indices) {
            val p = partition[j]
            val delta = normalize(p)
            result.children.add(p)
            result.starts.add(delta + childDelta)
            result.ends.add(max(p.ends) + childDelta + delta)
            if (j == 0) {
              result.ids.add(childId)
            }
            else {
              val newId = nextId--
              m = adopt(m, newId, p.ids)
              m.put(newId, parentId!!)
              result.ids.add(newId)
            }
          }
        }
        else {
          result.add(childId, node.starts[i], node.ends[i], child)
        }
      }
      ctx.nextId = nextId
      ctx.parentsMap = m
      result
    }
    else {
      node
    }
  }

  private fun childrenNeedMerging(node: Node, threshold: Int): Boolean {
    for (child in node.children) {
      if (child is Node) {
        if (child.children.size < threshold) {
          return true
        }
      }
      else {
        return false
      }
    }
    return false
  }

  private fun mergeNodes(leftDelta: Long, left: Node, rightDelta: Long, right: Node): Node {
    val capacity = left.children.size + right.children.size
    val delta = rightDelta - leftDelta
    val n = Node.empty(capacity)
    val leftc = left.children.size
    val rightc = right.children.size
    for (i in 0 until leftc) {
      n.children.add(left.children[i])
      n.starts.add(left.starts[i])
      n.ends.add(left.ends[i])
      n.ids.add(left.ids[i])
    }
    for (i in 0 until rightc) {
      n.children.add(right.children[i])
      n.starts.add(right.starts[i] + delta)
      n.ends.add(right.ends[i] + delta)
      n.ids.add(right.ids[i])
    }
    return n
  }

  private fun mergeChildren(ctx: EditingContext?, node: Node): Node {
    val splitThreshold = ctx!!.maxChildren
    val mergeThreshold = splitThreshold / 2
    return if (childrenNeedMerging(node, mergeThreshold)) {
      val result = Node.empty(mergeThreshold)
      var left = node.children[0] as Node
      var leftDelta = node.starts[0]
      var leftId = node.ids[0]
      var leftEnd = node.ends[0]
      for (i in 1 until node.children.size) {
        val right = node.children[i] as Node
        val rightDelta = node.starts[i]
        val rightId = node.ids[i]
        val rightEnd = node.ends[i]
        if (left.children.size < mergeThreshold || right.children.size < mergeThreshold) {
          val merged = mergeChildren(ctx, mergeNodes(leftDelta, left, rightDelta, right))
          if (merged.children.size > splitThreshold) {
            val split = splitNode(merged, splitThreshold)
            require(split.size == 2)
            ctx.parentsMap = adopt(ctx.parentsMap, leftId, split[0].ids)
            result.add(leftId, leftDelta, leftDelta + max(split[0].ends), split[0])
            ctx.parentsMap = adopt(ctx.parentsMap, rightId, split[1].ids)
            left = split[1]
            leftDelta += normalize(split[1])
            leftEnd = leftDelta + max(split[1].ends)
            leftId = rightId
          }
          else {
            left = merged
            leftEnd = leftDelta + max(merged.ends)
            ctx.parentsMap = adopt(ctx.parentsMap, leftId, right.ids)
          }
        }
        else {
          result.add(leftId, leftDelta, leftEnd, left)
          left = right
          leftDelta = rightDelta
          leftId = rightId
          leftEnd = rightEnd
        }
      }
      result.add(leftId, leftDelta, leftEnd, left)
      result
    }
    else {
      node
    }
  }

  fun balanceChildren(ctx: EditingContext?, node: Node): Node {
    return mergeChildren(ctx, splitChildren(ctx, node))
  }

  private fun intersects(s1: Long, e1: Long, s2: Long, e2: Long): Boolean {
    return if (s1 <= s2) s2 <= e1 else s1 <= e2
  }

  fun growTree(ctx: EditingContext?, rootId: Long, node: Node): Node {
    val balanced = balanceChildren(ctx, node)
    return if (balanced.children.size > ctx!!.maxChildren) {
      val newChildren = ArrayList<Any>()
      newChildren.add(balanced)
      val newLevelId = ctx.nextId--
      ctx.parentsMap = adopt(ctx.parentsMap, newLevelId, balanced.ids)
      ctx.parentsMap.put(newLevelId, rootId)
      val newRoot = Node(LongArrayList(longArrayOf(newLevelId)),
                         LongArrayList(longArrayOf(0)),
                         LongArrayList(longArrayOf(Long.MAX_VALUE)),
                         newChildren)
      growTree(ctx, rootId, newRoot)
    }
    else {
      balanced
    }
  }

  fun shrinkTree(ctx: EditingContext?, rootId: Long, root: Node): Node {
    return if (root.children.size == 1 && root.children[0] is Node) {
      val delta = root.starts[0]
      val child = root.children[0] as Node
      for (i in child.starts.indices) {
        child.starts[i] = child.starts[i] + delta
        child.ends[i] = child.ends[i] + delta
      }
      ctx!!.parentsMap = adopt(ctx.parentsMap, rootId, child.ids)
      shrinkTree(ctx, rootId, child)
    }
    else {
      root
    }
  }

  private fun normalize(node: Node): Long {
    return if (node.starts.size == 0) {
      0
    }
    else {
      val delta = node.starts[0]
      if (delta != 0L) {
        for (i in node.starts.indices) {
          node.starts[i] = node.starts[i] - delta
          node.ends[i] = node.ends[i] - delta
        }
      }
      delta
    }
  }

  fun <T : Any> batch(tree: IntervalsImpl<T>): Batch<T> {
    val ctx = EditingContext(tree.nextInnerId, tree.maxChildren, tree.dropEmpty, tree.parentsMap.builder())
    return Batch(Zipper.create(tree.openRoot, ctx, true),
                 Zipper.create(tree.closedRoot, ctx, false),
                 ctx)
  }

  private fun skipToOffset(z: Zipper?, offset: Long): Zipper? {
    for (i in z!!.idx until z.starts.size) {
      if (z.starts[i] + z.delta >= offset) {
        return if (Zipper.isBranch(z)) {
          val down = Zipper.downLeft(z)
          if (down == null) z else skipToOffset(down, offset)
        }
        else {
          z.idx = i - 1
          z
        }
      }
    }
    return if (z.hasRightCousin) {
      skipToOffset(Zipper.up(z), offset)
    }
    else {
      z.idx = z.starts.size - 1
      if (Zipper.isBranch(z)) {
        val down = Zipper.downLeft(z)
        if (down == null) z else skipToOffset(down, offset)
      }
      else {
        z
      }
    }
  }

  fun nextIntersection(zipper: Zipper, from: Long, to: Long): Zipper? {
    for (i in zipper.idx until zipper.starts.size) {
      if (intersects(from, to, zipper.starts[i] + zipper.delta, zipper.ends[i] + zipper.delta)) {
        zipper.idx = i
        return if (Zipper.isBranch(zipper)) {
          val down = Zipper.downLeft(zipper)
          if (down == null) null else nextIntersection(down, from, to)
        }
        else {
          zipper
        }
      }
    }
    val up = Zipper.up(zipper) ?: return null
    val skip = Zipper.skipRight(up)
    return if (skip == null) null else nextIntersection(skip, from, to)
  }

  fun prevIntersection(zipper: Zipper, from: Long, to: Long): Zipper? {
    for (i in zipper.idx downTo 0) {
      if (intersects(s1 = from, e1 = to,
                     s2 = zipper.starts[i] + zipper.delta,
                     e2 = zipper.ends[i] + zipper.delta)) {
        zipper.idx = i
        return if (Zipper.isBranch(zipper)) {
          val down = Zipper.downRight(zipper)
          if (down == null) null else prevIntersection(down, from, to)
        }
        else {
          zipper
        }
      }
    }
    val up = Zipper.up(zipper) ?: return null
    val skip = Zipper.skipLeft(up)
    return if (skip == null) null else prevIntersection(skip, from, to)
  }

  fun <T : Any> query(tree: IntervalsImpl<T>, from: Long, to: Long): IntervalsIterator<T> {
    var from = from
    var to = to
    require(from <= to) { "Not $from <= $to" }
    from = min(MAX_VALUE, from)
    to = min(MAX_VALUE, to)
    return MergingIterator(
      ForwardIterator(Zipper.create(tree.openRoot, null, true), from * 2, to * 2),
      ForwardIterator(Zipper.create(tree.closedRoot, null, false), from * 2, to * 2),
      IntervalsIterator.FORWARD_COMPARATOR)
  }

  fun <T : Any> queryReverse(tree: IntervalsImpl<T>, from: Long, to: Long): IntervalsIterator<T> {
    var from = from
    var to = to
    require(from <= to)
    from = min(MAX_VALUE, from)
    to = min(MAX_VALUE, to)
    return MergingIterator(
      BackwardIterator(Zipper.create(tree.openRoot, null, true), from * 2, to * 2),
      BackwardIterator(Zipper.create(tree.closedRoot, null, false), from * 2, to * 2),
      IntervalsIterator.BACKWARD_COMPARATOR)
  }

  fun expand(node: Node, offset: Long, len: Long): Node {
    val result = Node.empty(node.children.size)
    for (i in node.children.indices) {
      val id = node.ids[i]
      val start = node.starts[i]
      val end = node.ends[i]
      val child = node.children[i]
      if (start < offset && offset < end) {
        //....(interval)....
        //........o.........
        val c = if (child is Node) expand(child, offset - start, len) else child
        result.add(id, start, end + len, c)
      }
      else if (offset <= start) {
        //......(interval)....
        //....o...............
        result.add(id, start + len, end + len, child)
      }
      else {
        //....(interval)....
        //...............o....
        result.add(id, start, end, child)
      }
    }
    return result
  }

  fun <T : Any> expand(tree: IntervalsImpl<T>, start: Long, len: Long): IntervalsImpl<T> {
    return if (len == 0L) {
      tree
    }
    else IntervalsImpl(tree.maxChildren,
                       expand(tree.openRoot, start * 2, len * 2),
                       expand(tree.closedRoot, start * 2, len * 2),
                       tree.parentsMap,
                       tree.nextInnerId,
                       tree.dropEmpty)
  }

  private fun extinct(parentsMap: PersistentMap.Builder<Long, Long>, id: Long, child: Any): PersistentMap.Builder<Long, Long> {
    var parentsMap = parentsMap
    parentsMap.remove(id)
    if (child is Node) {
      for (i in child.ids.indices) {
        parentsMap = extinct(parentsMap, child.ids[i], child.children[i])
      }
    }
    return parentsMap
  }

  fun collapse(ctx: EditingContext, d: Long, node: Node, offset: Long, len: Long): Node {
    val result = Node.empty(node.children.size)
    for (i in node.children.indices) {
      val start = node.starts[i]
      val end = node.ends[i]
      val child = node.children[i]
      val id = node.ids[i]
      if (end <= offset) {
        // (interval)..............
        // .........[deletion]
        // interval is not affected by deletion range
        result.add(id, start, end, child)
      }
      else if (offset + len <= start) {
        //.............(interval)....
        //....[deletion].............
        // interval will move left
        result.add(id, start - len, end - len, child)
      }
      else if (ctx.dropEmpty && offset <= start && end <= offset + len) {
        //........(interval)........
        //....[....deletion....]....
        // entire interval will be deleted
        // just drop it on the floor
        ctx.parentsMap = extinct(ctx.parentsMap, id, child)
      }
      else {
        //....(....interval....)....
        //......[deletion]..........
        // or
        //........(interval)....
        //....[deletion]........
        // or
        //....(....interval....).....
        //................[deletion].
        if (child is Node) {
          var c = child
          c = collapse(ctx, d + start, c, offset - start, len)
          val delta = normalize(c)
          val newStart = start + delta
          result.add(id, newStart, newStart + max(c.ends), c)
        }
        else {
          val newStart = if (offset < start) max(offset - (d + start) % 2, start - len) else start
          val newEnd = max(offset + (d + end) % 2, end - len)
          if (ctx.dropEmpty && newEnd - newStart < 2) {
            ctx.parentsMap = extinct(ctx.parentsMap, id, child)
          }
          else {
            result.add(id, newStart, newEnd, child)
          }
        }
      }
    }
    return balanceChildren(ctx, result)
  }

  fun <T : Any> collapse(tree: IntervalsImpl<T>, start: Long, len: Long): IntervalsImpl<T> {
    if (len == 0L) {
      return tree
    }
    val ctx = EditingContext(tree.nextInnerId, tree.maxChildren, tree.dropEmpty, tree.parentsMap.builder())
    val openRoot = shrinkTree(ctx, OPEN_ROOT_ID,
                              growTree(ctx, OPEN_ROOT_ID,
                                       collapse(ctx, 0, tree.openRoot, start * 2, len * 2)))
    val closedRoot = shrinkTree(ctx, CLOSED_ROOT_ID,
                                growTree(ctx, CLOSED_ROOT_ID,
                                         collapse(ctx, 0, tree.closedRoot, start * 2, len * 2)))
    return IntervalsImpl(tree.maxChildren, openRoot, closedRoot, ctx.parentsMap.build(), ctx.nextId, tree.dropEmpty)
  }

  private fun remove(ctx: EditingContext, node: Node, nodeId: Long, subtree: HashMap<Long, HashSet<Long>>): Node {
    val victims = subtree[nodeId] ?: return node
    val copy = node.copy()
    for (vid in victims) {
      val vidx = copy.ids.indexOf(vid)
      val victim = copy.children[vidx]
      if (victim is Node) {
        val newNode = remove(ctx, victim, vid, subtree)
        val delta = normalize(newNode)
        copy.children[vidx] = newNode
        val start = copy.starts[vidx] + delta
        copy.starts[vidx] = start
        copy.ends[vidx] = max(newNode.ends) + start
      }
      else {
        copy.children.removeAt(vidx)
        copy.starts.removeAt(vidx)
        copy.ends.removeAt(vidx)
        copy.ids.removeAt(vidx)
        ctx.parentsMap.remove(vid)
      }
    }
    return balanceChildren(ctx, copy)
  }

  fun <T : Any> remove(tree: IntervalsImpl<T>, ids: Iterable<Long>): IntervalsImpl<T> {
    if (!ids.any()) return tree

    val deletionSubtree = deletionSubtree(tree.parentsMap, ids)
    val ctx = EditingContext(tree.nextInnerId, tree.maxChildren, tree.dropEmpty, tree.parentsMap.builder())
    val openRoot = shrinkTree(ctx, OPEN_ROOT_ID, remove(ctx, tree.openRoot, OPEN_ROOT_ID, deletionSubtree))
    val closedRoot = shrinkTree(ctx, CLOSED_ROOT_ID, remove(ctx, tree.closedRoot, CLOSED_ROOT_ID, deletionSubtree))
    return IntervalsImpl(tree.maxChildren,
                         openRoot,
                         closedRoot,
                         ctx.parentsMap.build(),
                         ctx.nextId,
                         tree.dropEmpty)
  }

  private fun resolvePath(parents: PersistentMap<Long, Long>, id: Long): LongArrayList? {
    val path = LongArrayList(4)
    var p: Long = id
    while (true) {
      p = parents[p] ?: return null
      path.add(p)
      if (p == OPEN_ROOT_ID || p == CLOSED_ROOT_ID) {
        return path
      }
    }
  }

  private fun chooseRoot(tree: IntervalsImpl<*>, id: Long): Node {
    return when (id) {
      OPEN_ROOT_ID -> tree.openRoot
      CLOSED_ROOT_ID -> tree.closedRoot
      else -> throw IllegalArgumentException("given id:$id")
    }
  }

  fun <T : Any> getById(tree: IntervalsImpl<T>, id: Long): Interval<Long, T>? {
    val path = resolvePath(tree.parentsMap, id) ?: return null
    var n = chooseRoot(tree, path[path.size - 1])
    var delta = 0
    for (i in path.size - 2 downTo 0) {
      val idx = n.ids.indexOf(path[i])
      if (idx == -1) {
        return null
      }
      else {
        delta += n.starts[idx].toInt()
        n = n.children[idx] as Node
      }
    }
    val idx = n.ids.indexOf(id)
    return if (idx == -1) {
      null
    }
    else {
      val from = n.starts[idx] + delta
      val to = n.ends[idx] + delta
      Interval(n.ids[idx],
               from / 2 + max(0, from % 2),
               to / 2,
               from % 2 != 0L,
               to % 2 != 0L,
               n.children[idx] as T)
    }
  }

  private fun deletionSubtree(parents: PersistentMap<Long, Long>, toBeDeleted: Iterable<Long>): HashMap<Long, HashSet<Long>> {
    val subtree = HashMap<Long, HashSet<Long>>()
    for (id in toBeDeleted) {
      require(id >= 0) { "id:$id" }
      var cid = id
      if (parents[cid] != null) {
        while (cid != OPEN_ROOT_ID && cid != CLOSED_ROOT_ID) {
          val pid = parents[cid] ?: throw NoSuchElementException("id:$cid")
          val sibs = subtree.getOrElse(pid) { HashSet() }
          sibs.add(cid)
          subtree[pid] = sibs
          cid = pid
        }
      }
    }
    return subtree
  }

  private fun max(arr: LongArrayList?): Long {
    var m = Long.MIN_VALUE
    for (i in arr!!.indices) {
      val l = arr[i]
      if (l > m) {
        m = l
      }
    }
    return m
  }

  class Node(val ids: LongArrayList,
             val starts: LongArrayList,
             val ends: LongArrayList,
             val children: ArrayList<Any>) {
    fun add(id: Long, start: Long, end: Long, child: Any) {
      ids.add(id)
      starts.add(start)
      ends.add(end)
      children.add(child)
    }

    fun copy(): Node {
      return Node(ids.clone(),
                  starts.clone(),
                  ends.clone(),
                  children.toMutableList() as ArrayList<Any>)
    }

    companion object {
      fun empty(capacity: Int): Node {
        return Node(ids = LongArrayList(capacity),
                    starts = LongArrayList(capacity),
                    ends = LongArrayList(capacity),
                    children = ArrayList(capacity))
      }
    }
  }

  class Zipper(val rootId: Long,
               var changed: Boolean,
               val hasRightCousin: Boolean,
               val hasLeftCousin: Boolean,
               val delta: Long,
               val parent: Zipper?,
               val rightCousinStart: Long,
               var starts: LongArrayList,
               var ends: LongArrayList,
               var ids: LongArrayList,
               var children: ArrayList<Any>,
               val editingContext: EditingContext?,
               var idx: Int) {

    companion object {
      private val ROOT_ENDS = LongArrayList(longArrayOf(Long.MAX_VALUE))
      private val ROOT_STARTS = LongArrayList(longArrayOf(0))
      private val OPEN_ROOT_IDS = LongArrayList(longArrayOf(OPEN_ROOT_ID))
      private val CLOSED_ROOT_IDS = LongArrayList(longArrayOf(CLOSED_ROOT_ID))
      fun create(root: Node, editingContext: EditingContext?, rootIsOpen: Boolean): Zipper {
        return Zipper(
          rootId = if (rootIsOpen) OPEN_ROOT_ID else CLOSED_ROOT_ID,
          rightCousinStart = Long.MAX_VALUE,
          hasRightCousin = false,
          hasLeftCousin = false,
          starts = ROOT_STARTS,
          ends = ROOT_ENDS,
          ids = if (rootIsOpen) OPEN_ROOT_IDS else CLOSED_ROOT_IDS,
          editingContext = editingContext,
          children = arrayListOf(root),
          idx = 0,
          changed = false,
          delta = 0L,
          parent = null
        )
      }

      fun id(z: Zipper): Long {
        return z.ids[z.idx]
      }

      fun from(z: Zipper): Long {
        val from = z.delta + z.starts[z.idx]
        return from / 2 + max(0, from % 2)
      }

      fun to(z: Zipper): Long {
        return (z.delta + z.ends[z.idx]) / 2
      }

      fun <T> data(z: Zipper): T {
        return z.children[z.idx] as T
      }

      fun isRoot(z: Zipper): Boolean {
        return z.parent == null
      }

      fun isBranch(z: Zipper): Boolean {
        return z.children.size > 0 && z.children[0] is Node
      }

      fun node(z: Zipper): Node {
        return z.children[z.idx] as Node
      }

      private fun down(z: Zipper, idx: Int): Zipper {
        val child = z.children[z.idx] as Node
        require(0 <= idx && idx < child.children.size)
        val hasRightCousin = z.hasRightCousin || z.idx < z.children.size - 1
        val rightCousinStart = when {
          hasRightCousin -> {
            (when {
              z.idx + 1 < z.starts.size -> z.starts[z.idx + 1]
              else -> z.rightCousinStart
            }) - z.starts[z.idx]
          }
          else -> {
            Long.MAX_VALUE
          }
        }
        require(rightCousinStart >= 0) { "rightCousinStart:$rightCousinStart" }

        return Zipper(
          parent = z,
          starts = child.starts,
          ends = child.ends,
          ids = child.ids,
          delta = z.delta + z.starts[z.idx],
          hasLeftCousin = z.hasLeftCousin || z.idx > 0,
          hasRightCousin = hasRightCousin,
          rightCousinStart = rightCousinStart,
          children = child.children,
          editingContext = z.editingContext,
          idx = idx,
          rootId = 0L,
          changed = false)
      }

      fun downLeft(z: Zipper): Zipper? {
        return when {
          isBranch(z) -> {
            val child = z.children[z.idx] as Node
            when (child.children.size) {
              0 -> null
              else -> down(z, 0)
            }
          }
          else -> throw IllegalArgumentException()
        }
      }

      fun downRight(z: Zipper): Zipper? {
        return if (isBranch(z)) {
          val child = z.children[z.idx] as Node
          if (child.children.size == 0) {
            null
          }
          else down(z, child.children.size - 1)
        }
        else {
          throw IllegalArgumentException()
        }
      }

      fun replace(p: Zipper, n: Node, delta: Long): Zipper {
        if (!p.changed) {
          p.ids = p.ids.clone()
          p.starts = p.starts.clone()
          p.ends = p.ends.clone()
          p.children = ArrayList(p.children)
        }
        p.children[p.idx] = n
        val newStart = p.starts[p.idx] + delta
        p.starts[p.idx] = newStart
        p.ends[p.idx] = newStart + max(n.ends)
        p.changed = true
        return p
      }

      fun left(z: Zipper): Zipper? {
        return when {
          z.idx - 1 >= 0 -> {
            z.idx -= 1
            z
          }
          else -> null
        }
      }

      fun right(z: Zipper): Zipper? {
        return when {
          z.idx + 1 < z.children.size -> {
            z.idx += 1
            z
          }
          else -> null
        }
      }

      fun skipRight(z: Zipper): Zipper? {
        val right = right(z)
        return right ?: if (z.hasRightCousin) skipRight(up(z)!!) else null
      }

      fun skipLeft(z: Zipper): Zipper? {
        val left = left(z)
        return left ?: if (z.hasLeftCousin) skipLeft(up(z)!!) else null
      }

      fun hasNext(z: Zipper?): Boolean {
        return z!!.idx + 1 < z.children.size || z.hasRightCousin || isBranch(z) && (z.children[z.idx] as Node).children.size > 0
      }

      fun next(z: Zipper): Zipper {
        var z: Zipper = z
        require(hasNext(z))
        do {
          z = if (isBranch(z)) downLeft(z)!! else skipRight(z)!!
        }
        while (isBranch(z))
        return z
      }

      fun remove(z: Zipper): Zipper {
        if (!z.changed) {
          z.ids = z.ids.clone()
          z.starts = z.starts.clone()
          z.ends = z.ends.clone()
          z.children = ArrayList(z.children)
        }
        z.changed = true
        z.ids.removeAt(z.idx)
        z.starts.removeAt(z.idx)
        z.ends.removeAt(z.idx)
        z.children.removeAt(z.idx)
        z.idx--
        return z
      }

      fun up(z: Zipper): Zipper? {
        return when {
          z.changed -> {
            val p = z.parent
            var n = Node(ids = z.ids,
                         starts = z.starts,
                         ends = z.ends,
                         children = z.children)
            val delta = if (p!!.parent == null) 0 else normalize(n)
            n = balanceChildren(z.editingContext, n)
            replace(p, n, delta)
            p
          }
          else -> z.parent
        }
      }

      fun root(z: Zipper): Node {
        var z = z
        while (!isRoot(z)) {
          z = up(z)!!
        }
        return shrinkTree(z.editingContext, z.rootId, growTree(z.editingContext, z.rootId, node(z)))
      }

      private fun findInsertionPoint(ss: LongArrayList?, o: Long): Int {
        // find nearest interval with start greater than insertion offset to preserve insertion order of markers with same start
        var i = 0
        while (i < ss!!.size && ss[i] <= o) {
          ++i
        }
        return i
      }

      fun <T : Any> insert(z: Zipper, from: Long, to: Long, closedLeft: Boolean, closedRight: Boolean, data: T): Zipper {
        return insert(z, --z.editingContext!!.nextId, from, to, closedLeft, closedRight, data)
      }

      fun <T : Any> insert(z: Zipper,
                           id: Long,
                           from: Long,
                           to: Long,
                           closedLeft: Boolean,
                           closedRight: Boolean,
                           data: T): Zipper {
        var z: Zipper? = z
        var from = from
        var to = to
        from = from * 2 - if (closedLeft) 1 else 0
        to = to * 2 + if (closedRight) 1 else 0
        var retries = 0
        while (true) {
          require(++retries < 1000)
          if (from - z!!.delta <= z.rightCousinStart) {
            val insertIdx = findInsertionPoint(z.starts, from - z.delta)
            if (isBranch(z)) {
              z.idx = max(0, insertIdx - 1)
              val down = downLeft(z)
              if (down == null) {
                require(isRoot(z))
                val newRoot = Node.empty(z.editingContext!!.maxChildren)
                newRoot.add(id, from, to, data)
                z.editingContext!!.parentsMap.put(id, z.rootId)
                return replace(z, newRoot, 0)
              }
              z = down
            }
            else {
              if (!z.changed) {
                z.ids = z.ids.clone()
                z.starts = z.starts.clone()
                z.ends = z.ends.clone()
                z.children = ArrayList(z.children)
              }
              require(z.editingContext!!.parentsMap[id] == null) { "id is not unique:$id" }
              z.starts.add(insertIdx, from - z.delta)
              z.ends.add(insertIdx, to - z.delta)
              z.ids.add(insertIdx, id)
              z.children.add(insertIdx, data)
              val currentId = z.parent!!.ids[z.parent!!.idx]
              z.editingContext!!.parentsMap.put(id, currentId)
              z.changed = true
              z.idx = if (insertIdx <= z.idx) z.idx + 1 else z.idx
              return z
            }
          }
          else {
            z = up(z)
          }
        }
      }
    }
  }

  class EditingContext(var nextId: Long, val maxChildren: Int, val dropEmpty: Boolean, var parentsMap: PersistentMap.Builder<Long, Long>)
  class Batch<T : Any> internal constructor(private var openZipper: Zipper,
                                            private var closedZipper: Zipper,
                                            private var editingContext: EditingContext
  ) {
    private var lastSeenFrom = Long.MIN_VALUE
    fun add(id: Long, from: Long, to: Long, closedLeft: Boolean, closedRight: Boolean, data: T) {
      require(from >= lastSeenFrom) { "batch is not sorted" }
      lastSeenFrom = from
      if (from * 2 < from || to * 2 < to) {
        throw ArithmeticException("from: $from, to: $to")
      }
      if (closedLeft) {
        closedZipper = Zipper.insert(closedZipper, id, from, to, closedLeft = true, closedRight, data)
      }
      else {
        openZipper = Zipper.insert(openZipper, id, from, to, closedLeft = false, closedRight, data)
      }
    }

    fun commit(): IntervalsImpl<T> {
      return IntervalsImpl(editingContext.maxChildren,
                           Zipper.root(openZipper),
                           Zipper.root(closedZipper),
                           editingContext.parentsMap.build(),
                           editingContext.nextId,
                           editingContext.dropEmpty)
    }
  }

  internal abstract class AbstractIterator<T>(var z: Zipper?) : IntervalsIterator<T> {
    override fun greedyLeft(): Boolean {
      return (z!!.delta + z!!.starts[z!!.idx]) % 2 != 0L
    }

    override fun greedyRight(): Boolean {
      return (z!!.delta + z!!.ends[z!!.idx]) % 2 != 0L
    }

    override fun from(): Long {
      return Zipper.from(z!!)
    }

    override fun to(): Long {
      return Zipper.to(z!!)
    }

    override fun id(): Long {
      return Zipper.id(z!!)
    }

    override fun data(): T {
      return Zipper.data(z!!)
    }
  }

  internal class ForwardIterator<T>(z: Zipper?, private val queryFrom: Long, private val queryTo: Long) : AbstractIterator<T>(z) {
    override fun next(): Boolean {
      val next = when {
        Zipper.isRoot(z!!) -> z
        else -> Zipper.skipRight(z!!)
      }
      z = if (next == null) null else nextIntersection(next, queryFrom, queryTo)
      return z != null
    }
  }

  internal class BackwardIterator<T>(z: Zipper?, private val queryFrom: Long, private val queryTo: Long) : AbstractIterator<T>(z) {
    override fun next(): Boolean {
      val next = when {
        Zipper.isRoot(z!!) -> z
        else -> Zipper.skipLeft(z!!)
      }
      z = if (next == null) null else prevIntersection(next, queryFrom, queryTo)
      return z != null
    }
  }
}