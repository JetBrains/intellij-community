// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.bifurcan.nodes

import fleet.util.bifurcan.utils.Bits


/**
 * @author ztellman
 */
object ListNodes {
  private const val SHIFT_INCREMENT = 5
  const val MAX_BRANCHES: Int = 1 shl SHIFT_INCREMENT
  private const val BRANCH_MASK = MAX_BRANCHES - 1

  fun slice(node: Any?, editor: Any, start: Long, end: Long): Any {
    if (node is Array<*>) {
      return node.copyOfRange(start.toInt(), end.toInt())
    }
    else {
      return (node as Node?)!!.slice(start.toInt(), end.toInt(), editor)
    }
  }

  fun set(elements: Array<Any?>, idx: Int, value: Any?): Array<Any?> {
    val ary = arrayOfNulls<Any?>(elements.size)
    elements.copyInto(ary, 0, 0)
    ary[idx] = value
    return ary
  }

  fun pushLast(a: Node, b: Any, editor: Any): Node {
    return if (b is Node) {
      a.pushLast(b, editor)
    }
    else {
      a.pushLast(b as Array<Any?>, editor)
    }
  }

  class Node {
    val shift: Byte
    var isStrict: Boolean = false
    private var numNodes: Int = 0
    private var editor: Any? = null
    var offsets: LongArray = LongArray(2)
    var nodes: Array<Any?> = arrayOfNulls(2)

    // constructors
    constructor(editor: Any?, shift: Int) {
      this.editor = editor
      this.shift = shift.toByte()
      // maybe not needed, since default value
      this.numNodes = 0
      this.offsets = LongArray(2)
      this.nodes = arrayOfNulls(2)
    }

    private constructor(shift: Int) {
      this.shift = shift.toByte()
    }

    // invariants
    fun assertInvariants() {
      if (shift.toInt() == SHIFT_INCREMENT) {

        for (i in 0 until numNodes) {
          check(nodes[i] is Array<*>)
        }
      }
      else {
        for (i in 0 until numNodes) {
          check((nodes[i] as Node).shift.toInt() == (shift - SHIFT_INCREMENT))
        }
      }
    }

    // lookup
    fun first(): Array<Any?>? {
      if (numNodes == 0) {
        return null
      }

      var n: Node? = this
      while (n!!.shift > SHIFT_INCREMENT) {
        n = n.nodes[0] as Node?
      }
      return n.nodes[0] as Array<Any?>?
    }

    fun last(): Array<Any?>? {
      if (numNodes == 0) {
        return null
      }

      var n: Node = this
      while (n.shift > SHIFT_INCREMENT) {
        n = n.nodes[n.numNodes - 1] as Node
      }
      return n.nodes[n.numNodes - 1] as Array<Any?>?
    }

    fun nth(idx: Long, returnChunk: Boolean): Any {
      if (!isStrict) {
        return relaxedNth(idx, returnChunk)
      }

      var n: Node = this
      while (n.shift > SHIFT_INCREMENT) {
        val nodeIdx = ((idx ushr n.shift.toInt()) and BRANCH_MASK.toLong()).toInt()
        n = n.nodes[nodeIdx] as Node

        if (!n.isStrict) {
          return n.relaxedNth(idx, returnChunk)
        }
      }

      val chunk = n.nodes[((idx ushr SHIFT_INCREMENT) and BRANCH_MASK.toLong()).toInt()] as Array<Any>
      return if (returnChunk) chunk else chunk!![(idx and BRANCH_MASK.toLong()).toInt()]
    }

    private fun relaxedNth(idx: Long, returnChunk: Boolean): Any {
      // moved inside here to make nth() more inline-able

      var idx = idx
      idx = idx and Bits.maskBelow(shift + SHIFT_INCREMENT)

      var n: Node = this

      while (n.shift > SHIFT_INCREMENT) {
        val nodeIdx = n.indexOf(idx)
        idx -= n.offset(nodeIdx)
        n = n.nodes[nodeIdx] as Node
      }

      val nodeIdx = n.indexOf(idx)
      val chunk = n.nodes[nodeIdx] as Array<Any>
      return if (returnChunk) chunk else chunk[(idx - n.offset(nodeIdx)).toInt()]
    }

    private fun indexOf(idx: Long): Int {
      val estimate = (if (shift > 60) 0 else (idx ushr shift.toInt()) and BRANCH_MASK.toLong()).toInt()
      if (isStrict) {
        return estimate
      }
      else {
        for (i in estimate until nodes.size) {
          if (idx < offsets[i]) {
            return i
          }
        }
        return -1
      }
    }

    private fun offset(idx: Int): Long {
      return if (idx == 0) 0 else offsets[idx - 1]
    }

    // update
    fun set(editor: Any?, idx: Long, value: Any?): Node {
      if (editor !== this.editor) {
        return clone(editor).set(editor, idx, value)
      }

      val nodeIdx = indexOf(idx)
      if (shift.toInt() == SHIFT_INCREMENT) {
        nodes[nodeIdx] = set(
          nodes[nodeIdx] as Array<Any?>,
          (idx - offset(nodeIdx)).toInt(), value
        )
      }
      else {
        nodes[nodeIdx] =
          (nodes[nodeIdx] as Node?)!!.set(editor, idx - offset(nodeIdx), value)
      }
      return this
    }

    // misc
    fun size(): Long {
      return if (numNodes == 0) 0 else offsets[numNodes - 1]
    }

    fun concat(node: Node, editor: Any): Node {
      if (size() == 0L) {
        return node
      }
      else if (node.size() == 0L) {
        return this
      }

      // same level
      return if (shift == node.shift) {
        var newNode: Node = if (editor === this.editor) this else clone(editor)

        for (i in 0 until node.numNodes) {
          newNode = pushLast(newNode, node.nodes[i]!!, editor)
        }
        newNode
        // we're below
      }
      else if (shift < node.shift) {
        node.pushFirst(this, editor)
        // we're above
      }
      else {
        pushLast(node, editor)
      }
    }

    fun slice(start: Int, end: Int, editor: Any): Node {
      if (start == end) {
        return EMPTY
      }
      else if (start.toLong() == 0L && end.toLong() == size()) {
        return this
      }

      val startIdx = indexOf(start.toLong())
      val endIdx = indexOf((end - 1).toLong())

      var rn: Node = EMPTY

      // we're slicing within a single node
      if (startIdx == endIdx) {
        val offset = offset(startIdx)
        val child = slice(nodes[startIdx], editor, start - offset, end - offset)
        if (shift > SHIFT_INCREMENT) {
          return child as Node
        }
        else {
          rn = pushLast(rn, child, editor)
        }

        // we're slicing across multiple nodes
      }
      else {
        // first partial node

        val sLower = offset(startIdx)
        val sUpper = offset(startIdx + 1)
        rn = pushLast(rn, slice(nodes[startIdx], editor, start - sLower, sUpper - sLower), editor)

        // intermediate full nodes
        for (i in startIdx + 1 until endIdx) {
          rn = pushLast(rn, nodes[i]!!, editor)
        }

        // last partial node
        val eLower = offset(endIdx)
        rn = pushLast(rn, slice(nodes[endIdx], editor, 0, end - eLower), editor)
      }

      return rn
    }

    ///
    fun pushLast(chunk: Array<Any?>?, editor: Any?): Node {
      if (size() == 0L && shift > SHIFT_INCREMENT) {
        return pushLast(from(editor, chunk), editor)
      }

      val stack = arrayOfNulls<Node>(shift / SHIFT_INCREMENT)
      stack[0] = this
      for (i in 1 until stack.size) {
        val n = stack[i - 1]
        stack[i] = n!!.nodes[n.numNodes - 1] as Node?
      }

      // we need to grow a parent
      if (stack[stack.size - 1]!!.numNodes == MAX_BRANCHES) {
        return if (numNodes == MAX_BRANCHES
        ) Node(editor, shift + SHIFT_INCREMENT).pushLast(this, editor)
          .pushLast(chunk, editor)
        else pushLast(from(editor, chunk), editor)
      }

      for (i in stack.indices) {
        if (stack[i]!!.editor !== editor) {
          stack[i] = stack[i]!!.clone(editor)
        }
      }

      val parent = stack[stack.size - 1]
      if (parent!!.nodes.size == parent.numNodes) {
        parent.grow()
      }
      parent.offsets[parent.numNodes] = parent.size()
      parent.numNodes++

      for (i in stack.indices) {
        val n = stack[i]
        val lastIdx = n!!.numNodes - 1
        n.nodes[lastIdx] = if (i == stack.size - 1) chunk else stack[i + 1]
        n.offsets[lastIdx] += chunk!!.size.toLong()
        n.updateStrict()
      }

      return stack[0]!!
    }

    fun pushFirst(chunk: Array<Any?>?, editor: Any?): Node {
      if (size() == 0L && shift > SHIFT_INCREMENT) {
        return pushLast(chunk, editor)
      }

      val stack = arrayOfNulls<Node>(shift / SHIFT_INCREMENT)
      stack[0] = this
      for (i in 1 until stack.size) {
        val n = stack[i - 1]
        stack[i] = n!!.nodes[0] as Node?
      }

      // we need to grow a parent
      if (stack[stack.size - 1]!!.numNodes == MAX_BRANCHES) {
        return if (numNodes == MAX_BRANCHES
        ) Node(editor, shift + SHIFT_INCREMENT).pushLast(chunk, editor)
          .pushLast(this, editor)
        else pushFirst(from(editor, chunk), editor)
      }

      for (i in stack.indices) {
        if (stack[i]!!.editor !== editor) {
          stack[i] = stack[i]!!.clone(editor)
        }
      }

      val parent = stack[stack.size - 1]
      if (parent!!.nodes.size == parent.numNodes) {
        parent.grow()
      }
      parent.nodes.copyInto(parent.nodes, destinationOffset = 1, startIndex = 0, endIndex = parent.numNodes)
      parent.offsets.copyInto(parent.offsets, destinationOffset = 1, startIndex = 0, endIndex = parent.numNodes)


      parent.offsets[0] = 0
      parent.numNodes++

      for (i in stack.indices) {
        val n = stack[i]
        n!!.nodes[0] = if (i == stack.size - 1) chunk else stack[i + 1]
        for (j in 0 until n.numNodes) {
          n.offsets[j] += chunk?.size?.toLong() ?: 0
        }
        n.updateStrict()
      }

      return stack[0]!!
    }

    fun pushLast(node: Node, editor: Any?): Node {
      if (node.size() == 0L) {
        return this
      }

      // make sure `node` is properly nested
      if (size() == 0L && (shift - node.shift) > SHIFT_INCREMENT) {
        return pushLast(from(editor, node.shift + SHIFT_INCREMENT, node), editor)
      }

      if (shift < node.shift) {
        return node.pushFirst(this, editor)
      }
      else if (shift == node.shift) {
        return from(
          editor, shift + SHIFT_INCREMENT,
          this, node
        )
      }

      val stack = arrayOfNulls<Node>(if (numNodes == 0) 1 else (shift - node.shift) / SHIFT_INCREMENT)
      stack[0] = this
      for (i in 1 until stack.size) {
        val n = stack[i - 1]
        stack[i] = n!!.nodes[n.numNodes - 1] as Node?
      }

      // we need to grow a parent
      if (stack[stack.size - 1]!!.numNodes == MAX_BRANCHES) {
        return pushLast(from(editor, node.shift + SHIFT_INCREMENT, node), editor)
      }

      for (i in stack.indices) {
        if (stack[i]!!.editor !== editor) {
          stack[i] = stack[i]!!.clone(editor)
        }
      }

      val parent = stack[stack.size - 1]
      if (parent!!.nodes.size == parent.numNodes) {
        parent.grow()
      }
      parent.offsets[parent.numNodes] = parent.size()
      parent.numNodes++

      val nSize = node.size()

      for (i in stack.indices) {
        val n = stack[i]
        val lastIdx = n!!.numNodes - 1
        n.nodes[lastIdx] = if (i == stack.size - 1) node else stack[i + 1]
        assertInvariants()
        n.offsets[lastIdx] += nSize
        n.updateStrict()
      }

      return stack[0]!!
    }

    fun pushFirst(node: Node?, editor: Any?): Node {
      // pushLast() has special code for the empty node case

      check(size() > 0)
      if (node!!.size() == 0L) {
        return this
      }

      // we're below this node
      if (shift < node.shift) {
        return node.pushLast(this, editor)
      }
      else if (shift == node.shift) {
        return from(
          editor, shift + SHIFT_INCREMENT, node,
          this
        )
      }

      // extract the path of all nodes between the root and the node we're prepending
      val stack = arrayOfNulls<Node>(if (numNodes == 0) 1 else (shift - node.shift) / SHIFT_INCREMENT)
      stack[0] = this
      for (i in 1 until stack.size) {
        val n = stack[i - 1]
        stack[i] = n!!.nodes[0] as Node?
      }

      // we need to grow a parent
      if (stack[stack.size - 1]!!.numNodes == MAX_BRANCHES) {
        return pushFirst(from(editor, node.shift + SHIFT_INCREMENT, node), editor)
      }

      // clone all nodes that don't share our editor, giving us free reign to edit them
      for (i in stack.indices) {
        if (stack[i]!!.editor !== editor) {
          stack[i] = stack[i]!!.clone(editor)
        }
      }

      val parent = stack[stack.size - 1]
      if (parent!!.nodes.size == parent.numNodes) {
        parent.grow()
      }

      parent.nodes.copyInto(parent.nodes, destinationOffset = 1, startIndex = 0, endIndex = parent.numNodes)
      parent.offsets.copyInto(parent.offsets, destinationOffset = 1, startIndex = 0, endIndex = parent.numNodes)

      parent.numNodes++
      parent.offsets[0] = 0

      val nSize = node.size()

      for (i in stack.indices) {
        val n = stack[i]
        n!!.nodes[0] = if (i == stack.size - 1) node else stack[i + 1]
        for (j in 0 until n.numNodes) {
          n.offsets[j] += nSize
        }
        n.updateStrict()
      }

      return stack[0]!!
    }

    fun popFirst(editor: Any?): Node {
      val stack = arrayOfNulls<Node>(shift / SHIFT_INCREMENT)
      stack[0] = if (editor === this.editor) this else clone(editor)
      for (i in 1 until stack.size) {
        val n = stack[i - 1]
        stack[i] = n!!.nodes[0] as Node?
      }

      val parent = stack[stack.size - 1]
      val chunk = parent!!.nodes[0] as Array<Any>?

      for (i in stack.indices) {
        val n = stack[i]
        for (j in 0 until n!!.numNodes) {
          n.offsets[j] -= chunk!!.size.toLong()
        }
        n.updateStrict()

        if (n.offsets[0] == 0L) {
          // shift everything left

          n.numNodes--

          n.nodes.copyInto(n.nodes, destinationOffset = 0, startIndex = 1, endIndex = n.numNodes + 1)
          n.offsets.copyInto(n.offsets, destinationOffset = 0, startIndex = 1, endIndex = n.numNodes + 1)

          n.nodes[n.numNodes] = null
          n.offsets[n.numNodes] = 0
          n.updateStrict()

          // if we have a single child at the top, de-nest the tree
          if (i == 0) {
            while (stack[0]!!.shift > SHIFT_INCREMENT && stack[0]!!.numNodes == 1) {
              stack[0] = stack[0]!!.nodes[0] as Node?
            }
          }

          // no need to go any deeper
          break
        }
        else {
          if (stack[i + 1]!!.editor !== editor) {
            stack[i + 1] = stack[i + 1]!!.clone(editor)
          }
          n.nodes[0] = stack[i + 1]
        }
      }

      return stack[0]!!
    }

    fun popLast(editor: Any?): Node {
      val stack = arrayOfNulls<Node>(shift / SHIFT_INCREMENT)
      stack[0] = if (editor === this.editor) this else clone(editor)
      for (i in 1 until stack.size) {
        val n = stack[i - 1]
        stack[i] = n!!.nodes[n.numNodes - 1] as Node?
      }

      val parent = stack[stack.size - 1]
      val chunk = parent!!.nodes[parent.numNodes - 1] as Array<Any>?

      for (i in stack.indices) {
        val n = stack[i]
        val lastIdx = n!!.numNodes - 1
        n.offsets[lastIdx] -= chunk!!.size.toLong()

        if (n.offset(lastIdx + 1) == n.offset(lastIdx)) {
          // lop off the rightmost node

          n.numNodes--
          n.nodes[n.numNodes] = null
          n.offsets[n.numNodes] = 0
          n.updateStrict()

          // if we have a single child at the top, de-nest the tree
          if (i == 0) {
            while (stack[0]!!.shift > SHIFT_INCREMENT && stack[0]!!.numNodes == 1) {
              stack[0] = stack[0]!!.nodes[0] as Node?
            }
          }

          // no need to go any further
          break
        }
        else {
          if (stack[i + 1]!!.editor !== editor) {
            stack[i + 1] = stack[i + 1]!!.clone(editor)
          }
          n.nodes[lastIdx] = stack[i + 1]
          n.updateStrict()
        }
      }

      return stack[0]!!
    }

    private fun grow() {
      val o = LongArray(offsets.size shl 1)
      offsets.copyInto(o, destinationOffset = 0, startIndex = 0, endIndex = offsets.size)

      this.offsets = o

      val n = arrayOfNulls<Any>(nodes.size shl 1)
      nodes.copyInto(n, destinationOffset = 0, startIndex = 0, endIndex = nodes.size)

      this.nodes = n
    }

    private fun updateStrict() {
      isStrict = numNodes <= 1 || offset(numNodes - 1) == (numNodes - 1) * (1L shl shift.toInt())
    }

    private fun clone(editor: Any?): Node {
      val n = Node(shift.toInt())
      n.editor = editor
      n.numNodes = numNodes
      n.offsets = offsets.copyOf()
      n.nodes = nodes.copyOf()

      return n
    }

    companion object {
      val EMPTY: Node = Node(Any(), SHIFT_INCREMENT)

      private fun from(editor: Any?, shift: Int, child: Node): Node {
        return Node(editor, shift).pushLast(child, editor)
      }

      private fun from(editor: Any?, shift: Int, a: Node, b: Node): Node {
        return Node(editor, shift).pushLast(a, editor).pushLast(b, editor)
      }

      private fun from(editor: Any?, child: Array<Any?>?): Node {
        return Node(editor, SHIFT_INCREMENT).pushLast(child, editor)
      }
    }
  }
}