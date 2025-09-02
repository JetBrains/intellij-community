// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.*
import fleet.util.openmap.MutableBoundedOpenMap
import fleet.util.openmap.MutableOpenMap
import fleet.fastutil.ints.IntList

class MutableDb internal constructor(
  override val dbBefore: DB,
  override val defaultPart: Part,
  internal var index: Index,
  var queryCache: QueryCache
) : Mut {

  private var editor: Editor = Editor()

  override val meta: MutableOpenMap<ChangeScope> = MutableBoundedOpenMap.empty()

  override val original: Q get() = this

  override fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T> =
    cachedQueryImpl(queryCache, query)

  override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
    index.queryIndex(indexQuery)

  fun initPartition(part: Part) {
    index = index.setPartition(editor, part, Index.Partition.empty())
  }

  override fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty = run {
    val mutableNovelty = MutableNovelty()
    index = expansion.ops.fold(index) { index, t ->
      when (t) {
        is Op.Assert ->
          index.add(editor, t.eid, t.attribute, t.value, expansion.tx, mutableNovelty::add)

        is Op.Retract ->
          index.remove(editor, t.eid, t.attribute, t.value, mutableNovelty::add)

        is Op.AssertWithTX ->
          index.add(editor, t.eid, t.attribute, t.value, t.tx, mutableNovelty::add)
      }
    }
    queryCache = queryCache.invalidate(mutableNovelty)
    mutableNovelty.persistent()
  }

  override val mutableDb: MutableDb get() = this

  fun mergePartitionsFrom(db: DB) {
    index = index.mergePartitionsFrom(editor, db.index)
  }

  fun rollback(db: DB) {
    index = db.index
    editor = Editor()
  }

  override fun assertEntityExists(eid: EID, accessedAttribute: Attribute<*>?, referenceAttribute: Attribute<*>?): Nothing? =
    when {
      index.entityExists(eid) -> null
      else -> throwEntityDoesNotExist(eid, accessedAttribute, referenceAttribute)
    }

  fun snapshot(): DB =
    DB(
      index = index,
      queryCache = queryCache
    ).also {
      editor = Editor()
    }
}

fun Mut.enforcingUniquenessConstraints(parts: IntList): Mut = let { mut ->
  object : Mut by mut {
    override fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty {
      for (t in expansion.ops) {
        if (t is Op.Assert && t.attribute.schema.unique) {
          @Suppress("UNCHECKED_CAST")
          val existing = mut.mutableDb.index.queryIndex(IndexQuery.LookupUnique(t.attribute as Attribute<Any>, t.value, parts))
          if (existing != null && existing.eid != t.eid) {
            throw TxValidationException(
              "Cannot insert duplicate value ${t.value} for attribute ${displayAttribute(t.attribute)} which is marked as unique")
          }
        }
      }
      return mut.mutate(pipeline, expansion)
    }
  }
}