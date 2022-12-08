// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.extensions

import com.intellij.util.graph.CachingSemiGraph
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.GraphGenerator
import com.intellij.util.graph.InboundSemiGraph
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*

/**
 * All extensions can have an "order" attribute in their XML element that will affect the place where this extension will appear in the
 * [ExtensionPoint.getExtensions]. Possible values are "first", "last", "before ID" and "after ID" where ID
 * is another same-type extension ID. Values can be combined in a comma-separated way. E.g. if you wish to plug before some extension XXX
 * that has "first" as its order, you must be "first, before XXX". The same with "last".
 *
 * Extension ID can be specified in the "id" attribute of corresponding XML element. When you specify order, it's usually a good practice
 * to specify also id, to allow other plugin-writers to plug relatively to your extension.
 *
 * If some anchor id can't be resolved, the constraint is ignored.
 */
@ApiStatus.Internal
class LoadingOrder {
  // for debug only
  private val name: @NonNls String
  private val first: Boolean
  private val last: Boolean
  private val before: Set<String>
  private val after: Set<String>

  private constructor() {
    name = "ANY"
    first = false
    last = false
    before = emptySet()
    after = emptySet()
  }

  constructor(text: @NonNls String) {
    name = text
    var last = false
    var first = false
    var before: MutableSet<String>? = null
    var after: MutableSet<String>? = null
    for (s in text.splitToSequence(ORDER_RULE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }) {
      when {
        s.equals(FIRST_STR, ignoreCase = true) -> {
          first = true
        }
        s.equals(LAST_STR, ignoreCase = true) -> {
          last = true
        }
        s.startsWith(BEFORE_STR, ignoreCase = true) -> {
          if (before == null) {
            before = LinkedHashSet(2)
          }
          before.add(s.substring(BEFORE_STR.length).trim())
        }
        s.startsWith(BEFORE_STR_OLD, ignoreCase = true) -> {
          if (before == null) {
            before = LinkedHashSet(2)
          }
          before.add(s.substring(BEFORE_STR_OLD.length).trim())
        }
        s.startsWith(AFTER_STR, ignoreCase = true) -> {
          if (after == null) {
            after = LinkedHashSet(2)
          }
          after.add(s.substring(AFTER_STR.length).trim())
        }
        s.startsWith(AFTER_STR_OLD, ignoreCase = true) -> {
          if (after == null) {
            after = LinkedHashSet(2)
          }
          after.add(s.substring(AFTER_STR_OLD.length).trim())
        }
        else -> throw AssertionError("Invalid specification: $s; should be one of first, last, before <id> or after <id>")
      }
    }

    this.before = before ?: emptySet()
    this.after = after ?: emptySet()
    this.first = first
    this.last = last
  }

  companion object {
    const val FIRST_STR: @NonNls String = "first"
    const val LAST_STR: @NonNls String = "last"
    const val BEFORE_STR: @NonNls String = "before "
    const val BEFORE_STR_OLD: @NonNls String = "before:"
    const val AFTER_STR: @NonNls String = "after "
    const val AFTER_STR_OLD: @NonNls String = "after:"
    const val ORDER_RULE_SEPARATOR: Char = ','

    @JvmField
    val ANY = LoadingOrder()
    @JvmField
    val FIRST = LoadingOrder(FIRST_STR)
    @JvmField
    val LAST = LoadingOrder(LAST_STR)

    @JvmStatic
    fun before(id: @NonNls String?): LoadingOrder {
      return LoadingOrder(BEFORE_STR + id)
    }

    @JvmStatic
    fun after(id: @NonNls String?): LoadingOrder {
      return LoadingOrder(AFTER_STR + id)
    }

    fun sort(orderable: MutableList<out Orderable>) {
      if (orderable.size < 2) {
        return
      }

      // our graph is pretty sparse so do benefit from the fact
      val map = LinkedHashMap<String, Orderable>()
      val cachedMap = LinkedHashMap<Orderable, LoadingOrder>()
      val first = LinkedHashSet<Orderable>(1)
      val hasBefore = LinkedHashSet<Orderable>(orderable.size)
      for (o in orderable) {
        val id = o.orderId
        if (!id.isNullOrEmpty()) {
          @Suppress("ReplacePutWithAssignment")
          map.put(id, o)
        }
        val order = o.order
        if (order === ANY) {
          continue
        }

        cachedMap.put(o, order)
        if (order.first) {
          first.add(o)
        }
        if (!order.before.isEmpty()) {
          hasBefore.add(o)
        }
      }

      if (cachedMap.isEmpty()) {
        return
      }

      val graph = object : InboundSemiGraph<Orderable> {
        override fun getNodes(): Collection<Orderable> = orderable.reversed()

        override fun getIn(n: Orderable): Iterator<Orderable> {
          val order = cachedMap.getOrDefault(n, ANY)
          val predecessors = LinkedHashSet<Orderable>()
          for (id in order.after) {
            val o = map.get(id)
            if (o != null) {
              predecessors.add(o)
            }
          }
          val id = n.orderId
          if (!id.isNullOrEmpty()) {
            for (o in hasBefore) {
              val hisOrder = cachedMap.getOrDefault(o, ANY)
              if (hisOrder.before.contains(id)) {
                predecessors.add(o)
              }
            }
          }
          if (order.last) {
            for (o in orderable) {
              val hisOrder = cachedMap.getOrDefault(o, ANY)
              if (!hisOrder.last) {
                predecessors.add(o)
              }
            }
          }
          if (!order.first) {
            predecessors.addAll(first)
          }
          return predecessors.iterator()
        }
      }

      val builder = DFSTBuilder(GraphGenerator.generate(CachingSemiGraph.cache(graph)))
      if (!builder.isAcyclic) {
        val p = builder.circularDependency!!
        throw SortingException("Could not satisfy sorting requirements", p.key, p.value)
      }
      orderable.sortWith(builder.comparator())
    }

    @JvmStatic
    fun readOrder(orderAttr: String?): LoadingOrder {
      return when (orderAttr) {
        null -> ANY
        FIRST_STR -> FIRST
        LAST_STR -> LAST
        else -> LoadingOrder(orderAttr)
      }
    }
  }

  override fun toString(): String = name

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is LoadingOrder) {
      return false
    }
    return first == other.first && last == other.last && after == other.after && before == other.before
  }

  override fun hashCode(): Int {
    var result = if (first) 1 else 0
    result = 31 * result + if (last) 1 else 0
    result = 31 * result + before.hashCode()
    result = 31 * result + after.hashCode()
    return result
  }

  interface Orderable {
    val orderId: String?
    val order: LoadingOrder
  }
}