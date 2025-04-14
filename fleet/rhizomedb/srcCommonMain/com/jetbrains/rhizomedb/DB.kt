// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.*
import kotlin.jvm.JvmStatic

data class DB(
  val index: Index,
  val queryCache: QueryCache,
) : Q {

  companion object {
    private val EMPTY = lazy {
      DB(
        index = Index.withPartitions(Array(MAX_PART + 1) { Index.Partition.empty() }),
        queryCache = QueryCache.empty()
      ).change {
        register(EntityType)
        registerMixin(Entity)
        register(EntityAttribute)
      }.dbAfter
    }

    @JvmStatic
    fun empty(): DB = EMPTY.value
  }

  override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
    index.queryIndex(indexQuery)

  override val original: Q get() = this

  override fun assertEntityExists(eid: EID, accessedAttribute: Attribute<*>?, referenceAttribute: Attribute<*>?): Nothing? =
    when {
      index.entityExists(eid) -> null
      else -> throwEntityDoesNotExist(eid, accessedAttribute, referenceAttribute)
    }

  override fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T> =
    cachedQueryImpl(queryCache, query)

  fun mutable(defaultPart: Part): MutableDb =
    MutableDb(dbBefore = this,
              defaultPart = defaultPart,
              queryCache = queryCache,
              index = index)

  fun selectPartitions(parts: Set<Int>): DB =
    copy(index = index.select(Editor(), parts),
         queryCache = QueryCache.empty())

  override fun equals(other: Any?): Boolean =
    other is DB && other.index == this.index

  override fun hashCode(): Int =
    index.hashCode()

  override fun toString(): String =
    "DB[${index.hashCode()}]"
}
