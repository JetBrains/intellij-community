// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.bifurcan

import fleet.util.BifurcanListSerializer
import fleet.util.bifurcan.nodes.ListNodes.Node
import fleet.util.bifurcan.utils.Bits.log2Ceil
import fleet.util.bifurcan.nodes.ListNodes

import kotlin.math.min
import kotlinx.collections.immutable.PersistentList
import kotlinx.serialization.Serializable

/**
 * An implementation of an immutable list which allows for elements to be added and removed from both ends of the
 * collection, and random-access reads and writes.  Due to its
 * [relaxed radix structure](https://infoscience.epfl.ch/record/169879/files/RMTrees.pdf), `slice()`,
 * `concat()`, and `split()` are near-constant time.
 *
 * @author ztellman
 */
@Serializable(with = BifurcanListSerializer::class)
open class List<V> : PersistentList<V> {
  private var root: Node
  private var prefixLen: Int
  private var suffixLen: Int
  private var prefix: Array<Any?>?
  var suffix: Array<Any?>?
  private val editor: Any?

  constructor() {
    this.editor = null
    this.root = Node.EMPTY
    this.prefixLen = 0
    this.prefix = null
    this.suffixLen = 0
    this.suffix = null
  }

  protected constructor(
    linear: Boolean,
    root: Node,
    prefixLen: Int,
    prefix: Array<Any?>?,
    suffixLen: Int,
    suffix: Array<Any?>?
  ) {
    this.editor = if (linear) Any() else null
    this.root = root
    this.prefixLen = prefixLen
    this.suffixLen = suffixLen
    this.prefix = prefix
    this.suffix = suffix
  }

  fun nth(idx: Long, defaultValue: V?): V? {

    val rootSize: Long = root.size()
    if (idx < 0 || idx >= (rootSize + prefixLen + suffixLen)) {
      return defaultValue
    }

    // look in the prefix
    return if (idx < prefixLen) {
      prefix!![(prefix!!.size + idx - prefixLen).toInt()] as V?

      // look in the tree
    }
    else if (idx - prefixLen < rootSize) {
      root.nth(idx - prefixLen, false) as V?

      // look in the suffix
    }
    else {
      suffix!![(idx - (rootSize + prefixLen)).toInt()] as V?
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is List<*>) return false

    if (this.size() != other.size()) return false

    for (i in 0L until size()) {
      if (this.nth(i) != other.nth(i)) return false
    }

    return true

  }

  fun nth(idx: Long): V {
    val rootSize: Long = root.size()
    if (idx < 0 || idx >= (rootSize + prefixLen + suffixLen)) {
      throw IndexOutOfBoundsException(idx.toString() + " must be within [0," + size() + ")")
    }

    // look in the prefix
    return if (idx < prefixLen) {
      prefix!![(prefix!!.size + idx - prefixLen).toInt()] as V

      // look in the tree
    }
    else if (idx - prefixLen < rootSize) {
      root.nth(idx - prefixLen, false) as V

      // look in the suffix
    }
    else {
      suffix!![(idx - (rootSize + prefixLen)).toInt()] as V
    }
  }

  fun size(): Long {
    return root.size() + prefixLen + suffixLen
  }

  val isLinear: Boolean
    get() = editor != null

  fun addLast(value: V): List<V> {
    return (if (isLinear) this else clone()).pushLast(value)
  }

  override fun add(element: V): List<V> {
    return addLast(element)
  }

  fun addFirst(value: V): List<V> {
    return (if (isLinear) this else clone()).pushFirst(value)
  }

  override fun addAll(elements: Collection<V>): List<V> {
    var l = if (isLinear) this else clone()

    elements.forEach { elem -> l = l.addLast(elem) }
    return l
  }

  fun removeLast(): List<V> {
    return (if (isLinear) this else clone()).popLast()
  }

  fun removeFirst(): List<V> {
    return (if (isLinear) this else clone()).popFirst()
  }

  override fun remove(element: V): List<V> {
    val ind = indexOf(element)
    return if (ind == -1) this else removeAt(ind)
  }

  override fun removeAll(elements: Collection<V>): List<V> {
    var l = if (isLinear) this else clone()
    for (elem in elements) {
      l = l.remove(elem)
    }
    return l
  }

  override fun removeAll(predicate: (V) -> Boolean): List<V> {
    val l = if (isLinear) this else clone()
    return l.removeAll(this.filter(predicate))

  }

  override fun removeAt(index: Int): List<V> {
    var l = (if (isLinear) this else clone())
    when (index) {
      0 -> {
        l = l.removeFirst()
      }
      l.size - 1 -> {
        l = l.removeLast()
      }
      else -> {
        // splice the tree in two forgoing the element at the given index
        val left = l.slice(0, index.toLong())
        val right = l.slice(index.toLong() + 1, l.size())
        l = left.concat(right)
      }
    }
    return l
  }

  override fun retainAll(elements: Collection<V>): List<V> {
    val l = if (isLinear) this else clone()
    val elementsSet = HashSet(elements)
    return l.removeAll { elem -> !elementsSet.contains(elem) }
  }

  override val size: Int
    get() = size().toInt()

  override fun builder(): PersistentList.Builder<V> {
    TODO("Not yet implemented")
  }

  override fun clear(): List<V> {
    var l = (if (isLinear) this else clone())
    for (x in 1..l.size) {
      l = l.removeFirst()
    }
    return l
  }

  override fun get(index: Int): V {
    return this.nth(index.toLong())
  }

  override fun isEmpty(): Boolean {
    return this.size() == 0L
  }

  override fun indexOf(element: V): Int {
    // since we cannot determine where in the
    // tree an element sits without its index, we can only do linear search
    this.forEachIndexed { index, value ->
      if (value != null) {
        if (value == element) return index
      }
    }
    return -1
  }

  override fun containsAll(elements: Collection<V>): Boolean {
    elements.forEach { elem -> if (!this.contains(elem)) return false }
    return true
  }

  override fun contains(element: V): Boolean {
    return this.indexOf(element) != -1
  }

  override fun addAll(index: Int, c: Collection<V>): PersistentList<V> {
    var l = if (isLinear) this else clone()
    when (index) {
      0 -> {
        // list reversed to keep the relative order in the collection when adding to front
        c.reversed().forEach { elem -> l.addFirst(elem) }
      }
      l.size - 1 -> {
        c.forEach { elem -> l.addFirst(elem) }
      }
      else -> {
        val left = l.slice(0, index.toLong())
        c.forEach { elem -> l.addLast(elem) }
        val right = l.slice(index.toLong(), l.size())
        l = left.concat(right)
      }
    }
    return l
  }

  override fun set(index: Int, element: V): List<V> {
    return set(index.toLong(), element)
  }

  override fun add(index: Int, element: V): List<V> {
    // splice at index, insert at end and append again
    var l = if (isLinear) this else clone()
    if (0 > index || index >= l.size) {
      throw IndexOutOfBoundsException()
    }
    when (index) {
      0 -> {
        l = l.addFirst(element)
      }
      else -> {
        // splice the tree in two and insert the element at the index
        var left = l.slice(0, index.toLong())
        left = left.addLast(element)
        val right = l.slice(index.toLong(), l.size())
        l = left.concat(right)
      }
    }
    return l


  }


  fun set(idx: Long, value: V): List<V> {
    val size = size().toInt()
    if (idx < 0 || idx > size) {
      throw IndexOutOfBoundsException()
    }

    return if (idx == size.toLong()) {
      addLast(value)
    }
    else {
      (if (isLinear) this else clone()).overwrite(idx.toInt(), value)
    }
  }

  override fun iterator(): Iterator<V> {
    val initChunk: Array<Any?>?
    val initOffset: Int
    val initLimit: Int
    val size = size()
    val rootSize: Long = root.size()

    if (prefixLen > 0) {
      initChunk = prefix
      initOffset = pIdx(0)
      initLimit = prefix!!.size
    }
    else if (rootSize > 0) {

      val result = root.nth(0, true)
      initChunk = result as? Array<Any?> ?: arrayOf(result)
      initOffset = 0
      initLimit = initChunk.size
    }
    else {
      initChunk = suffix
      initOffset = 0
      initLimit = suffixLen
    }

    return object : Iterator<V> {
      var idx: Long = 0

      var chunk: Array<Any?>? = initChunk
      var offset: Int = initOffset
      var limit: Int = initLimit
      var chunkSize: Int = limit - offset

      override fun hasNext(): Boolean {
        return idx < size
      }

      override fun next(): V {
        val value = chunk!![offset++] as V

        if (offset == limit) {
          idx += chunkSize.toLong()
          if (idx < size) {
            if (idx == prefixLen + rootSize) {
              chunk = suffix
              limit = suffixLen
            }
            else {
              val result = root.nth(idx - prefixLen, true)
              chunk = result as? Array<Any?> ?: arrayOf(result)

              limit = chunk!!.size
            }
            offset = 0
            chunkSize = limit
          }
        }

        return value
      }
    }
  }

  override fun listIterator(): ListIterator<V> {
    return listIterator(0)
  }

  override fun listIterator(index: Int): ListIterator<V> {
    if (index !in 0 until this.size()) throw IndexOutOfBoundsException(index.toString() + " must be within [0," + size() + ")")
    val iter = this.iterator()
    repeat(index) { if (iter.hasNext()) iter.next() }
    return iter as ListIterator<V>
  }

  override fun lastIndexOf(element: V): Int {
    return this.indexOfLast { elem -> elem == element }
  }

  fun slice(start: Long, end: Long): List<V> {
    if (start < 0 || end > size()) {
      throw IndexOutOfBoundsException("[" + start + "," + end + ") isn't a subset of [0," + size() + ")")
    } else if (end <= start) {
      return List()
    }

    val pStart = min(prefixLen.toDouble(), start.toDouble()).toInt()
    val pEnd = min(prefixLen.toDouble(), end.toDouble()).toInt()
    val pLen = pEnd - pStart
    var pre: Array<Any?>? = null
    if (pLen > 0) {
      pre = arrayOfNulls(1 shl log2Ceil(pLen.toLong()))
      val prefixArray = prefix
      prefixArray!!.copyInto(pre, destinationOffset = pre.size - pLen, startIndex = pIdx(pStart), endIndex = pIdx(pStart) + pLen)
    }

    val sStart = ((start - (prefixLen + root.size())).toInt()).coerceAtLeast(0)
    val sLen = (((end - (prefixLen + root.size())).toInt()) - sStart).coerceAtLeast(0)
    var suf: Array<Any?>? = null
    if (sLen > 0) {
      suf = arrayOfNulls(1 shl log2Ceil(sLen.toLong()))
      suffix!!.copyInto(suf, destinationOffset = 0, startIndex = sStart, endIndex = sStart + sLen)

    }

    return List(
      isLinear,
      root.slice(
        (min(root.size(), start - prefixLen).toInt()).coerceAtLeast(0),
        (min(root.size(), end - prefixLen).toInt()).coerceAtLeast(0),
        Any()
      ),
      pLen, pre, sLen, suf
    )
  }

  fun concat(l: PersistentList<V>): List<V> {
    val b = l as List<V>
    var r: Node = root
    val editor = Any()

    // append our own suffix
    if (suffixLen > 0) {
      r = r.pushLast(suffixArray(), editor)
    }

    // append their prefix
    if (b.prefixLen > 0) {
      r = r.pushLast(b.prefixArray(), editor)
    }

    if (b.root.size() > 0) {
      r = r.concat(b.root, editor)
    }

    return List(
      isLinear, r,
      prefixLen, if (prefixLen > 0) prefix!!.copyOf() else null,
      b.suffixLen, if (b.suffixLen > 0) b.suffix!!.copyOf() else null
    )
  }

  fun forked(): List<V> {
    return if (isLinear) List<V>(
      false,
      root,
      prefixLen,
      prefix,
      suffixLen,
      suffix
    ).clone()
    else this
  }

  fun linear(): List<V> {
    return if (isLinear) this
    else List<V>(
      true,
      root,
      prefixLen,
      prefix,
      suffixLen,
      suffix
    ).clone()
  }

  fun clone(): List<V> {
    return List(
      isLinear, root,
      prefixLen, if (prefix == null) null else prefix?.copyOf(),
      suffixLen, if (suffix == null) null else suffix?.copyOf()
    )
  }

  private fun suffixArray(): Array<Any?> {
    return suffix?.copyOfRange(0, suffixLen)!!
  }

  private fun prefixArray(): Array<Any?> {
    return prefix?.copyOfRange(pIdx(0), pIdx(0) + prefixLen)!!
  }

  private fun pIdx(idx: Int): Int {
    return prefix!!.size - prefixLen + idx
  }

  fun overwrite(idx: Int, value: V): List<V> {
    val rootSize: Long = root.size()

    // overwrite prefix
    if (idx < prefixLen) {
      prefix!![prefix!!.size - prefixLen + idx] = value

      // overwrite tree
    }
    else if (idx < (prefixLen + rootSize)) {
      root = root.set(editor, (idx - prefixLen).toLong(), value)

      // overwrite suffix
    }
    else {
      suffix!![(idx - (prefixLen + rootSize)).toInt()] = value
    }

    return this
  }

  fun pushFirst(value: V): List<V> {
    if (prefix == null) {
      prefix = arrayOfNulls(2)
    }
    else if (prefixLen == prefix!!.size) {
      val newPrefix = arrayOfNulls<Any>(
        min(MAX_CHUNK_SIZE.toDouble(), (prefix!!.size shl 1).toDouble())
          .toInt()
      )
      prefix!!.copyInto(newPrefix, destinationOffset = newPrefix.size - prefixLen, startIndex = 0, endIndex = prefixLen)
      prefix = newPrefix
    }

    prefix!![pIdx(-1)] = value
    prefixLen++

    if (prefixLen == MAX_CHUNK_SIZE) {
      val editor = if (isLinear) this.editor else Any()
      root = root.pushFirst(prefix, editor)
      prefix = null
      prefixLen = 0
    }

    return this
  }

  fun pushLast(value: V): List<V> {
    if (suffix == null) {
      suffix = arrayOfNulls(2)
    }
    else if (suffixLen == suffix!!.size) {
      val newSuffix = arrayOfNulls<Any>(
        min(MAX_CHUNK_SIZE.toDouble(), (suffix!!.size shl 1).toDouble())
          .toInt()
      )
      suffix!!.copyInto(newSuffix, destinationOffset = 0, startIndex = 0, endIndex = suffix!!.size)

      suffix = newSuffix
    }

    suffix!![suffixLen++] = value

    if (suffixLen == MAX_CHUNK_SIZE) {
      val editor = if (isLinear) this.editor else Any()
      root = root.pushLast(suffix, editor)
      suffix = null
      suffixLen = 0
    }

    return this
  }

  fun popFirst(): List<V> {
    if (prefixLen == 0) {
      if (root.size() > 0) {
        val chunk: Array<Any?>? = root.first()
        if (chunk != null) {
          val editor = if (isLinear) this.editor else Any()
          prefix = chunk.copyOf()
          prefixLen = prefix!!.size
          root = root.popFirst(editor)
        }
      }
      else if (suffixLen > 0) {
        suffix!!.copyInto(suffix!!, destinationOffset = 0, startIndex = 1, endIndex = suffixLen)
        suffixLen -= 1
        suffix!![suffixLen] = null
      }
    }

    if (prefixLen > 0) {
      prefixLen--
      prefix!![pIdx(-1)] = null
    }

    return this
  }

  fun popLast(): List<V> {
    if (suffixLen == 0) {
      if (root.size() > 0) {
        val chunk: Array<Any?>? = root.last()
        if (chunk != null) {
          val editor = if (isLinear) this.editor else Any()
          suffix = chunk.copyOf()
          suffixLen = suffix!!.size
          root = root.popLast(editor)
        }
      }
      else if (prefixLen > 0) {
        prefixLen--
        prefix!!.copyInto(prefix!!, destinationOffset = pIdx(0), startIndex = pIdx(-1), endIndex = pIdx(-1) + prefixLen)

        prefix!![pIdx(-1)] = null
      }
    }

    if (suffixLen > 0) {
      suffix!![--suffixLen] = null
    }
    return this
  }

  companion object {
    val EMPTY: List<*> = List<Any?>()

    fun <V> of(vararg elements: V): List<V> {
      val list = List<V>().linear()
      for (e in elements) {
        list.addLast(e)
      }
      return list.forked()
    }

    fun <V> from(list: PersistentList<V>): List<V> {
      return if (list is List<*>) {
        (list as List<V>).forked()
      }
      else {
        from(list.iterator())
      }
    }

    fun <V> from(iterable: Iterable<V>): List<V> {
      return from(iterable.iterator())
    }

    fun <V> from(iterator: Iterator<V>): List<V> {
      val list = List<V>().linear()
      while (iterator.hasNext()) {
        list.add(iterator.next())
      }
      return list.forked()
    }

    fun <V> empty(): List<V> {
      return EMPTY as List<V>
    }

    private const val MAX_CHUNK_SIZE: Int = ListNodes.MAX_BRANCHES
  }

}