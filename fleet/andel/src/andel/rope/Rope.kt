// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.rope

import andel.rope.impl.*
import andel.rope.impl.Node
import andel.rope.impl.buildRope
import andel.rope.impl.zipper
import kotlin.jvm.JvmInline

/**
 * Abstract Rope data structure.
 * Logically rope represents a single contineous value but partitioned into small chunks for faster access and modification.
 * One may think of it as a map-reduce for data.
 * For this reason rope may not be empty, it always contains at least one chunk.
 * Internally Rope is a tree, which maintains a partial sums of certain monotounous metric for each subtree recursively.
 * Later one can access these data, stored in leaves by iteration or logarithmic scan by any of the stored metrics.
 * To define a rope over some abstract type [T], one has to implement [Monoid] interface by some global object.
 * See [andel.text.impl.TextMonoid] for a good example.
 *
 * To access or to modify rope, use [cursor] method.
 * */
class Rope<T> internal constructor(
  internal val root: Node,
  internal val rootMetrics: Metrics,
  internal val monoid: Monoid<T>,
) {

  override fun equals(other: Any?): Boolean =
    other is Rope<*> && other.root == root


  override fun hashCode(): Int =
    root.hashCode()
  
  /**
   * Pointer to some chunk, located in [Rope].
   * Cursor always points at some [element].
   * It knows it's [location] and [size] by each [Metric].
   *
   * Cursor is modeled as persitent/linear data type.
   * Methods that change location of the cursor, take `owner` parameter, which represents the owner of the value.
   * If owner is equal to the previous owner of the value, the previous value is considered to be destroyed and can't be used anymore.
   * To preserve the previous Cursor, pass a new owner
   * */
  class Cursor<T> internal constructor(
    private val monoid: Monoid<T>,
    private val zipper: Zipper<T>,
  ) {

    /**
     * Chunk value it currently points at
     * */
    val element: T
      get() = zipper.data()

    /**
     * Returns the size of a current [element] by [metric] metric.
     * */
    fun size(metric: Metric): Int =
      zipper.size(monoid.rank, metric)

    /**
     * Returns the location of the current chunk in [Rope].
     * It is defined as accumulated value of a [metric] of all chunks **before** the current one.
     * */
    fun location(metric: Metric): Int =
      zipper.location(monoid.rank, metric)

    /**
     * Replaces the current [element] with a new one.
     * Returned Cursor points to the newly emplaced chunk.
     * If [owner] is the same as before, the previous Cursur is recycled and it's internals are reused for creating a new one.
     * If [owner] is the new value, @receiver is still usable and points into the same location of the old version of a rope.
     * */
    fun replace(owner: Any?, element: T): Cursor<T> =
      cursor(zipper.replace(owner, monoid, element, monoid.measure(element)))

    /**
     * Moves the cursor to a new (or the same) location to the right of the current one, defined as the first chunk, which's right border is strictly smaller than provided value.
     * It's easier to explain using the predicate:
     * `result.location(metric) <= value < result.location(metric) + resutl.size(metric)`
     *
     * if the value is bigger or equal to the entire rope metric, the resulting cursor simply points to the last chunk.
     *
     * If [owner] is the same as before, the previous Cursur is recycled and it's internals are reused for creating a new one.
     * */
    fun scan(owner: Any?, metric: Metric, value: Int): Cursor<T> =
      cursor(
        when {
          value < zipper.location(monoid.rank, metric) -> zipper.rope(owner, monoid).zipper(owner)
          else -> zipper
        }.scan(owner, monoid, value, metric)!!
      )

    /**
     * Returns a pointer to the next chunk.
     * If [owner] is the same as before, the previous Cursor is recycled and it's internals are reused for creating a new one.
     * */
    fun next(owner: Any?): Cursor<T>? =
      zipper.nextLeaf(owner, monoid)?.let { leaf ->
        cursor(leaf)
      }

    /**
     * Returns a pointer to the prev chunk.
     * If [owner] is the same as before, the previous Cursor is recycled and it's internals are reused for creating a new one.
     * */
    fun prev(owner: Any?): Cursor<T>? =
      zipper.prevLeaf(owner, monoid)?.let { leaf ->
        cursor(leaf)
      }

    /**
     * Builds a new rope, accomodating all changes made to the cursor.
     * If [owner] is the same as before, the previous Cursor is recycled and it's internals are reused.
     * */
    fun rope(owner: Any?): Rope<T> =
      zipper.rope(owner, monoid)

    private fun cursor(zip: Zipper<T>): Cursor<T> =
      if (zip === zipper) this else Cursor(monoid, zip)
  }

  /**
   * Returns the size of the full rope in respect to a particular metric [metric]
   * */
  fun size(metric: Metric): Int =
    rootMetrics[metric]

  /**
   * Builds a new cursor, with the provided [owner].
   * It is pointed at the very first chunk.
   * */
  fun cursor(owner: Any?): Cursor<T> =
    Cursor(monoid, zipper(owner).downToLeaf(owner, monoid)!!)
}

@JvmInline
value class Metric internal constructor(val id: Int)

@JvmInline
value class Metrics(val metrics: IntArray) {
  operator fun get(i: Metric): Int = metrics[i.id]
}

/**
 * To use Rope, one has to first define a corresponding Monoid type.
 * Implement it in some global object and use it later to create [Rope] using [ropeOf] method.
 * To implement a monoid, one has to provide:
 *
 * 1. A way to split and merge chunks of type [T].
 *    Chunks are being merged together if they are to small(their [leafSize] is smaller than [leafSplitThresh] / 2).
 *    Chunks are being split if their [leafSize] is bigger than [leafSplitThresh].
 *    Please choose [leafSplitThresh] constant wisely.
 * 2. Define some number of (no more than 31) metrics using [maxMetric] and [sumMetric] for a chunk.
 *    Each metric is an integer value, accumulated using sum or maximum operation.
 *    Define them as contants in your Monoid object and use them later in [Rope.Cursor.scan].
 *
 * Metrics must be consistent with respect to merge and split:
 * For each defined metric `M` and chunk `t`:
 * - `metrics(M, merge(t1, t2)) == acc(M, metrics(t1), metrics(t2))`
 * - `metrics(M, t) == acc(M, *split(t))`
 *
 * For good example see [andel.text.impl.TextMonoid]
 * */
abstract class Monoid<T>(val leafSplitThresh: Int) {

  /**
   * Defines a new [Metric] in this monoid to be accumulated using max operation.
   * */
  protected fun maxMetric(): Metric {
    val rank = rank++
    bitMask = BitMask(bitMask.bitMask or (1 shl rank))
    return Metric(rank)
  }

  /**
   * Defines a new [Metric] in this monoid to be accumulated using sum operation.
   * */
  protected fun sumMetric(): Metric {
    val rank = rank++
    return Metric(rank)
  }

  /**
   * Retrun all metrics for given Chunk (in order they're defined)
   * */
  abstract fun measure(data: T): Metrics

  /**
   * Return a size of a given chunk in terms of memory consumption.
   * It might or might not correspond to one of metrics.
   * If the value returned from here is bigger than [leafSplitThresh], the chunk will be split.
   * */
  abstract fun leafSize(leaf: T): Int

  /**
   * Provide a way to merge two chunks. We do it when they are too small (smaller than [leafSplitThresh]/2)
   * */
  abstract fun merge(leafData1: T, leafData2: T): T

  /**
   * Provide a way to split a chunk which is determined to be too big (bigger than [leafSplitThresh]) into a list of smaller ones.
   * */
  abstract fun split(leaf: T): List<T>

  var rank: Int = 0
    private set

  internal var bitMask = BitMask(0)

  /**
   * Builds a new rope for provided list of chunks.
   * */
  fun ropeOf(elements: List<T>): Rope<T> = buildRope(elements, this)
}

