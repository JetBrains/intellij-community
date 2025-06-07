// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.bifurcan.nodes

import fleet.util.BifurcanVector
import kotlin.collections.Map.Entry

object SortedMapNodes {
  fun <K, V> min(n: Branch<K, V>): Branch<K, V> {
    var n = n
    while (true) {
      if (n.l !is Branch) {
        return n
      }
      else {
        n = n.l
      }
    }
  }

  fun <K, V> red(l: Node<K, V>, k: K, v: V, r: Node<K, V>): Branch<K, V> {
    return Branch<K, V>(Color.RED, l, k, v, r)
  }

  fun <K, V> black(l: Node<K, V>, k: K, v: V, r: Node<K, V>): Branch<K, V> {
    return Branch<K, V>(Color.BLACK, l, k, v, r)
  }

  fun <K, V> node(c: Color, l: Node<K, V>, k: K, v: V, r: Node<K, V>): Branch<K, V> {
    return Branch<K, V>(c, l, k, v, r)
  }

  fun <K, V> slice(n: Branch<K, V>, min: K, max: K, comparator: Comparator<K>): Branch<K, V>? {
    return null
  }

  fun <K, V> find(n: Node<K, V>, key: K, comparator: Comparator<K>): Branch<K, V>? {
    var n = n
    while (true) {
      if (n !is Branch) {
        return null
      }

      val cmp = comparator.compare(key, n.k)
      if (cmp < 0) {
        n = n.l
      }
      else if (cmp > 0) {
        n = n.r
      }
      else {
        return n
      }
    }
  }

  fun <K, V> indexOf(n: Node<K, V>, key: K, comparator: Comparator<K>): Long {
    var n = n
    var idx: Long = 0
    while (true) {
      if (n !is Branch) {
        return -1
      }

      val cmp = comparator.compare(key, n.k)
      if (cmp < 0) {
        n = n.l
      }
      else if (cmp > 0) {
        idx += n.l.size + 1
        n = n.r
      }
      else {
        return idx + n.l.size
      }
    }
  }

  fun <K, V> nth(n: Node<K, V>, idx: Int): Branch<K, V> {
    var n = n as Branch
    var idx = idx
    while (true) {
      if (idx >= n.l.size) {
        idx -= (n.l.size + 1).toInt()
        if (idx == -1) {
          return n
        }
        else {
          n = n.r as Branch
        }
      }
      else {
        n = n.l as Branch
      }
    }
  }

  fun <K, V> iterator(root: Node<K, V>): Iterator<Entry<K, V>> {
    if (root !is Branch) {
      return iterator { }
    }

    return object : Iterator<Entry<K, V>> {
      val stack: Array<Branch<K, V>?> = arrayOfNulls<Branch<K, V>>(64)
      val cursor: ByteArray = ByteArray(64)
      var depth: Int = 0

      init {
        stack[0] = root
        nextValue()
      }

      fun nextValue() {
        while (depth >= 0) {
          val n = stack[depth]!!
          when (cursor[depth].toInt()) {
            0 -> {
              if (n.l !is Branch) {
                cursor[depth]++
                return
              }
              else {
                stack[++depth] = n.l
                cursor[depth] = 0
              }
            }
            1 -> return
            2 -> {
              if (n.r !is Branch) {
                if (--depth >= 0) {
                  cursor[depth]++
                }
              }
              else {
                stack[++depth] = n.r
                cursor[depth] = 0
              }
            }
            3 -> if (--depth >= 0) {
              cursor[depth]++
            }
          }
        }
      }

      override fun hasNext(): Boolean {
        return depth >= 0
      }

      override fun next(): Entry<K, V> {
        val n = stack[depth]!!
        val e = object : Entry<K, V> {
          override val key: K = n.k
          override val value: V = n.v
        }
        cursor[depth]++
        nextValue()
        return e
      }
    }
  }

  enum class Color {
    RED, BLACK, DOUBLE_BLACK
  }

  sealed class Node<K, V> {
    abstract val c: Color
    abstract val size: Long

    abstract fun copy(c: Color): Node<K, V>

    fun redden(): Node<K, V> {
      return if (c == Color.BLACK && this is Branch && l.c == Color.BLACK && r.c == Color.BLACK) node<K, V>(Color.RED, l, k, v, r)
      else this
    }

    fun blacken(): Node<K, V> {
      return if (c == Color.RED) copy(Color.BLACK)
      else this
    }

    fun unblacken(): Node<K, V> {
      return if (c == Color.DOUBLE_BLACK) copy(Color.BLACK)
      else this
    }

    fun remove(key: K, comparator: Comparator<K>): Node<K, V> {
      return redden()._remove(key, comparator)
    }

    private fun _remove(key: K, comparator: Comparator<K>): Node<K, V> {
      if (this !is Branch) {
        return this
      }
      else {
        val cmp = comparator.compare(key, k)
        if (cmp < 0) {
          return node<K, V>(c, l._remove(key, comparator), k, v, r).rotate()
        }
        else if (cmp > 0) {
          return node<K, V>(c, l, k, v, r._remove(key, comparator)).rotate()
        }
        else if (size == 1L) {
          return (if (c == Color.BLACK) DoubleBlackLeaf else BlackLeaf) as Node<K, V>
        }
        else if (r !is Branch) {
          return l.blacken()
        }
        else {
          val min = min<K, V>(r)
          return node<K, V>(c, l, min.k, min.v, r.removeMin()).rotate()
        }
      }
    }

    fun put(key: K, value: V, merge: (V, V) -> V, comparator: Comparator<K>): Node<K, V> {
      return _put(key, value, merge, comparator).blacken()
    }

    private fun _put(key: K, value: V, merge: (V, V) -> V, comparator: Comparator<K>): Node<K, V> {
      if (this !is Branch) {
        return node<K, V>(if (c == Color.DOUBLE_BLACK) Color.BLACK else Color.RED, BlackLeaf as Node<K, V>, key, value, BlackLeaf)
      }
      else {
        val cmp = comparator.compare(key, this.k)
        if (cmp < 0) {
          return node<K, V>(c, l._put(key, value, merge, comparator), k, v, r).balance()
        }
        else if (cmp > 0) {
          return node<K, V>(c, l, k, v, r._put(key, value, merge, comparator)).balance()
        }
        else {
          return node<K, V>(c, l, key, merge(v, value), r)
        }
      }
    }

    fun split(targetSize: Int, acc: BifurcanVector<Node<K, V>>) {
      if (this is Branch && size >= targetSize * 2) {
        val offset = acc.size()
        l.split(targetSize, acc)
        if (acc.size() > offset) {
          acc.set(offset, node<K, V>(c, acc.nth(offset), k, v, BlackLeaf as Node<K, V>))
          r.split(targetSize, acc)
        }
        else {
          r.split(targetSize, acc)
          acc.set(offset, node<K, V>(c, BlackLeaf as Node<K, V>, k, v, acc.nth(offset)))
        }
      }
      else if (size > 0) {
        acc.addLast(this)
      }
    }

    fun balance(): Node<K, V> {
      if (this !is Branch) {
        return this
      }
      else if (c == Color.BLACK) {
        return balanceBlack()
      }
      else if (c == Color.DOUBLE_BLACK) {
        return balanceDoubleBlack()
      }
      else {
        return this
      }
    }

    fun rotate(): Node<K, V> {
      if (this !is Branch) {
        return this
      }

      if (c == Color.RED) { // (R (BB? a-x-b) y (B czd))
        // (balance (B (R (-B a-x-b) y c) z d))
        if (l.c == Color.DOUBLE_BLACK && r.c == Color.BLACK && r is Branch) {
          return black<K, V>(red<K, V>(l.unblacken(), k, v, r.l), r.k, r.v, r.r).balance()
        }

        // (R (B axb) y (BB? c-z-d))
        // (balance (B a x (R b y (-B c-z-d))))
        if (r.c == Color.DOUBLE_BLACK && l.c == Color.BLACK && l is Branch) {
          return black<K, V>(l.l, l.k, l.v, red<K, V>(l.r, k, v, r.unblacken())).balance()
        }
      }
      else if (c == Color.BLACK) { // (B (BB? a-x-b) y (B czd))
        // (balance (BB (R (-B a-x-b) y c) z d))

        if (l.c == Color.DOUBLE_BLACK && r.c == Color.BLACK && r is Branch) {
          return node<K, V>(Color.DOUBLE_BLACK, red<K, V>(l.unblacken(), k, v, r.l), r.k, r.v, r.r).balance()
        }

        // (B (B axb) y (BB? c-z-d))
        // (balance (BB a x (R b y (-B c-z-d))))
        if (l.c == Color.BLACK && r.c == Color.DOUBLE_BLACK && l is Branch) {
          return node<K, V>(Color.DOUBLE_BLACK, l.l, l.k, l.v, red<K, V>(l.r, k, v, r.unblacken())).balance()
        }

        // (B (BB? a-w-b) x (R (B cyd) z e))
        // (B (balance (B (R (-B a-w-b) x c) y d)) z e)
        if (l.c == Color.DOUBLE_BLACK && r.c == Color.RED && r is Branch && r.l.c == Color.BLACK && r.l is Branch) {
          val rl = r.l
          return black<K, V>(black<K, V>(red<K, V>(l.unblacken(), k, v, rl.l), rl.k, rl.v, rl.r).balance(), r.k, r.v, r.r)
        }

        // (B (R a w (B bxc)) y (BB? d-z-e))
        // (B a w (balance (B b x (R c y (-B d-z-e)))))
        if (l.c == Color.RED && l is Branch && l.r is Branch && l.r.c == Color.BLACK && r.c == Color.DOUBLE_BLACK) {
          val lr = l.r
          return black<K, V>(l.l, l.k, l.v, black<K, V>(lr.l, lr.k, lr.v, red<K, V>(lr.r, k, v, r.unblacken())).balance())
        }
      }

      return this
    }

    fun floorIndex(key: K, comparator: Comparator<K>, offset: Long): Long {
      if (this !is Branch) {
        return -1
      }

      val cmp = comparator.compare(key, k)
      if (cmp > 0) {
        val idx = r.floorIndex(key, comparator, offset + l.size + 1)
        return if (idx < 0) offset + l.size else idx
      }
      else if (cmp < 0) {
        return l.floorIndex(key, comparator, offset)
      }
      else {
        return offset + l.size
      }
    }

    fun ceilIndex(key: K, comparator: Comparator<K>, offset: Long): Long {
      if (this !is Branch) {
        return -1
      }

      val cmp = comparator.compare(key, k)
      if (cmp > 0) {
        return r.ceilIndex(key, comparator, offset + l.size + 1)
      }
      else if (cmp < 0) {
        val idx = l.ceilIndex(key, comparator, offset)
        return if (idx < 0) offset + l.size else idx
      }
      else {
        return offset + l.size
      }
    }

    fun slice(min: K, max: K, comparator: Comparator<K>): Node<K, V> {
      if (this !is Branch) {
        return this
      }

      if (comparator.compare(k, min) < 0) {
        return r.slice(min, max, comparator)
      }

      if (comparator.compare(k, max) > 0) {
        return l.slice(min, max, comparator)
      }

      return node<K, V>(c, l.slice(min, max, comparator), k, v, r.slice(min, max, comparator)).rotate()
    }

    fun <U> mapValues(f: (K, V) -> U): Node<K, U> {
      return if (this !is Branch) this as Node<K, U>
      else Branch<K, U>(c, l.mapValues<U>(f), k, f(k, v), r.mapValues<U>(f))
    }

    fun checkInvariant(): Int {
      check(c != Color.DOUBLE_BLACK)

      if (this !is Branch) {
        return 1
      }

      check(!(c == Color.RED && (l.c == Color.RED || r.c == Color.RED)))

      val ld = l.checkInvariant()
      val rd = r.checkInvariant()

      check(ld == rd)

      var n = ld
      if (c == Color.BLACK) {
        n++
      }

      return n
    }
  }

  object BlackLeaf : Node<Nothing, Nothing>() {
    override val c: Color = Color.BLACK
    override val size: Long = 0

    override fun copy(c: Color): Node<Nothing, Nothing> = when (c) {
      Color.RED -> error("It is not possible to make leaf red")
      Color.BLACK -> this
      Color.DOUBLE_BLACK -> DoubleBlackLeaf
    }
  }

  object DoubleBlackLeaf : Node<Nothing, Nothing>() {
    override val c: Color = Color.DOUBLE_BLACK
    override val size: Long = 0

    override fun copy(c: Color): Node<Nothing, Nothing> = when (c) {
      Color.RED -> error("It is not possible to make leaf red")
      Color.BLACK -> BlackLeaf
      Color.DOUBLE_BLACK -> this
    }
  }


  class Branch<K, V>(
    override val c: Color,
    val l: Node<K, V>,
    val k: K,
    val v: V,
    val r: Node<K, V>,
  ) : Node<K, V>() {
    override val size: Long = l.size + r.size + 1

    override fun copy(c: Color): Node<K, V> = Branch(c, l, k, v, r)

    internal fun removeMin(): Node<K, V> {
      if (l !is Branch) {
        if (c == Color.RED) {
          return BlackLeaf as Node<K, V>
        }
        else if (r.size == 0L) {
          return DoubleBlackLeaf as Node<K, V>
        }
        else {
          return r.blacken()
        }
      }

      return node<K, V>(c, l.removeMin(), k, v, r).rotate()
    }

    internal fun balanceBlack(): Branch<K, V> {
      if (l.c == Color.RED && l is Branch) { // (B (R (R a x b) y c) z d)
        // (R (B a x b) y (B c z d))
        if (l.l.c == Color.RED) {
          return red<K, V>(l.l.blacken(), l.k, l.v, black<K, V>(l.r, k, v, r))
        }

        // (B (R a x (R b y c)) z d)
        // (R (B a x b) y (B c z d))
        if (l.r.c == Color.RED && l.r is Branch) {
          val lr = l.r
          return red<K, V>(black<K, V>(l.l, l.k, l.v, lr.l), lr.k, lr.v, black<K, V>(lr.r, k, v, r))
        }
      }

      if (r.c == Color.RED && r is Branch) { // (B a x (R (R b y c) z d))
        // (R (B a x b) y (B c z d))
        if (r.l.c == Color.RED && r.l is Branch) {
          val rl = r.l
          return red<K, V>(black<K, V>(l, k, v, rl.l), rl.k, rl.v, black<K, V>(rl.r, r.k, r.v, r.r))
        }

        // (B a x (R b y (R c z d))
        // (R (B a x b) y (B c z d))
        if (r.r.c == Color.RED) {
          return red<K, V>(black<K, V>(l, k, v, r.l), r.k, r.v, r.r.blacken())
        }
      }

      return this
    }

    internal fun balanceDoubleBlack(): Branch<K, V> { // (BB (R a x (R b y c)) z d)
      // (B (B a x b) y (B c z d))
      if (l.c == Color.RED && l is Branch && l.r.c == Color.RED && l.r is Branch) {
        val lr = l.r
        return black<K, V>(black<K, V>(l.l, l.k, l.v, lr.l), lr.k, lr.v, black<K, V>(lr.r, k, v, r))
      }

      // (BB a x (R (R b y c) z d))
      // (B (B a x b) y (B c z d))
      if (r.c == Color.RED && r is Branch && r.l.c == Color.RED && r.l is Branch) {
        val rl = r.l
        return black<K, V>(black<K, V>(l, k, v, rl.l), rl.k, rl.v, black<K, V>(rl.r, r.k, r.v, r.r))
      }

      return this
    }
  }
}