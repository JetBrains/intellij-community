// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.rpc.core.AssumptionsViolatedException
import fleet.util.serialization.ISerialization
import fleet.util.UID
import it.unimi.dsi.fastutil.longs.LongArrayList

internal fun Mut.queryRecording(
  serContext: InstructionEncodingContext,
  recorder: (InstructionsPair) -> Unit,
  eidToUid: DbContext<Q>.(EID) -> UID?,
): Mut = let { mut ->
  object : Mut by mut {
    override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
      when (val sharedQuery = asOf(mut) { encodeQuery(indexQuery, serContext.json, eidToUid) }) {
        null -> mut.queryIndex(indexQuery)
        else -> {
          val (result, trace) = mut.traceQuery(indexQuery)
          recorder(
            InstructionsPair(
              localInstruction = Validate(indexQuery, trace),
              sharedInstruction = ValidateCoder.sharedInstruction(SharedValidate(sharedQuery, trace)),
              sharedNovelty = Novelty.Empty,
              sharedEffects = emptyList()
            )
          )
          result
        }
      }
  }
}

data class Validate(
  val indexQuery: IndexQuery<*>?,
  val trace: Long,
) : Instruction {
  override val seed: Long get() = 0

  override fun DbContext<Q>.expand(): InstructionExpansion =
    InstructionExpansion(emptyList<Op>().also {
      val t = when {
        indexQuery != null -> impl.traceQuery(indexQuery).second
        else -> EMPTY_TRACE
      }
      if (t != trace) {
        throw AssumptionsViolatedException("$indexQuery expected hash: $trace was: $t")
      }
    })
}

internal fun Mut.expandingWithReadTracking(): Mut = let { mut ->
  object : Mut by mut {
    override fun expand(pipeline: DbContext<Q>, instruction: Instruction): Expansion =
      expandInstructionWithReadTracking(pipeline, mut, instruction)
  }
}

internal fun expandInstructionWithReadTracking(pipeline: DbContext<Q>, mut: Mut, instruction: Instruction): Expansion {
  val queryHashes = LongArrayList()
  queryHashes.add(instruction.seed)
  val q = pipeline.impl.original
  val expansion = pipeline.alter(object : Q by q {
    override fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T> {
      throw UnsupportedOperationException()
    }

    override fun <T> queryIndex(indexQuery: IndexQuery<T>): T {
      val (result, trace) = q.traceQuery(indexQuery)
      queryHashes.add(trace)
      return result
    }
  }) {
    mut.expand(this, instruction)
  }
  return expansion.copy(tx = queryHashes.trace())
}

private fun LongArrayList.trace(): Long {
  sort()
  val i = iterator()
  var h = 1L
  var s = size
  while (s-- != 0) {
    h = 31 * h + it.unimi.dsi.fastutil.HashCommon.mix(i.nextLong())
  }
  return h
}

private fun Sequence<Datom>.trace(): Long {
  val txs = LongArrayList()
  forEach { d ->
    if (partition(d.eid) == SharedPart && d.attr != Entity.EntityObject.attr) {
      txs.add(d.tx)
    }
  }
  return txs.trace()
}

private fun List<Datom>.trace(): Long {
  val txs = LongArrayList(size)
  forEach { d ->
    if (partition(d.eid) == SharedPart && d.attr != Entity.EntityObject.attr) {
      txs.add(d.tx)
    }
  }
  return txs.trace()
}

internal val EMPTY_TRACE = LongArrayList.of().trace()

@Suppress("UNCHECKED_CAST")
internal fun <T> Q.traceQuery(indexQuery: IndexQuery<T>): Pair<T, Long> =
  queryIndex(indexQuery).let { result ->
    result to when (indexQuery) {
      is IndexQuery.All -> {
        (result as Sequence<Datom>).trace()
      }
      is IndexQuery.Column<*> -> {
        (result as List<Datom>).trace()
      }
      is IndexQuery.Contains<*> -> {
        when {
          indexQuery.attribute == Entity.EntityObject.attr -> EMPTY_TRACE
          result == null -> EMPTY_TRACE
          partition(indexQuery.eid) == SharedPart -> LongArrayList.of(result as TX).trace()
          else -> EMPTY_TRACE
        }
      }
      is IndexQuery.Entity -> {
        (result as List<Datom>).trace()
      }
      is IndexQuery.GetMany<*> -> {
        (result as List<Datom>).trace()
      }
      is IndexQuery.GetOne<*> -> {
        when {
          indexQuery.attribute == Entity.EntityObject.attr -> EMPTY_TRACE
          result == null -> EMPTY_TRACE
          partition(indexQuery.eid) == SharedPart -> LongArrayList.of((result as Versioned<*>).tx).trace()
          else -> EMPTY_TRACE
        }
      }
      is IndexQuery.LookupMany<*> -> (result as List<Datom>).trace()
      is IndexQuery.LookupUnique<*> -> {
        when {
          indexQuery.attribute == Entity.EntityObject.attr -> EMPTY_TRACE
          result == null -> EMPTY_TRACE
          partition((result as VersionedEID).eid) == SharedPart -> LongArrayList.of(result.tx).trace()
          else -> EMPTY_TRACE
        }
      }
      is IndexQuery.RefsTo -> (result as List<Datom>).trace()
    }
  }

/*
* null returned from here should be treated as a silent query which doesn't need to be validated by remote kernel
*/
internal fun <T> DbContext<Q>.encodeQuery(
  indexQuery: IndexQuery<T>,
  json: ISerialization,
  eidToUid: DbContext<Q>.(EID) -> UID?,
): SharedQuery? =
  when (indexQuery) {
    is IndexQuery.All -> {
      error("All query should not be performed in shared block")
    }
    is IndexQuery.Column<*> -> {
      attributeIdent(indexQuery.attribute)?.let { attribute ->
        SharedQuery.Column(attribute)
      }
    }
    is IndexQuery.Contains<*> -> {
      attributeIdent(indexQuery.attribute)?.let { attribute ->
        eidToUid(indexQuery.eid)?.let { uid ->
          serialize1(json, eidToUid, indexQuery.attribute, indexQuery.value)?.let { dbValue ->
            SharedQuery.Contains(uid, attribute, dbValue)
          }
        }
      }
    }
    is IndexQuery.Entity -> {
      eidToUid(indexQuery.eid)?.let { uid ->
        SharedQuery.Entity(uid)
      }
    }
    is IndexQuery.GetMany<*> -> {
      attributeIdent(indexQuery.attribute)?.let { attribute ->
        eidToUid(indexQuery.eid)?.let { uid ->
          SharedQuery.GetMany(uid, attribute)
        }
      }
    }
    is IndexQuery.GetOne<*> -> {
      when (indexQuery.attribute) {
        uidAttribute() -> null
        else -> attributeIdent(indexQuery.attribute)?.let { attribute ->
          eidToUid(indexQuery.eid)?.let { uid ->
            SharedQuery.GetOne(uid, attribute)
          }
        }
      }
    }
    is IndexQuery.LookupMany<*> -> {
      attributeIdent(indexQuery.attribute)?.let { attribute ->
        serialize1(json, eidToUid, indexQuery.attribute, indexQuery.value)?.let { dbValue ->
          SharedQuery.LookupMany(attribute, dbValue)
        }
      }
    }
    is IndexQuery.LookupUnique<*> -> {
      attributeIdent(indexQuery.attribute)?.let { attribute ->
        serialize1(json, eidToUid, indexQuery.attribute, indexQuery.value)?.let { dbValue ->
          SharedQuery.LookupMany(attribute, dbValue)
        }
      }
    }
    is IndexQuery.RefsTo -> {
      eidToUid(indexQuery.eid)?.let { uid ->
        SharedQuery.RefsTo(uid)
      }
    }
  }

/*
* null means, that the query is referring to a dead entity, the result of such query is considered to be empty even without running it
* */
@Suppress("UNCHECKED_CAST")
internal fun DbContext<Q>.decodeQuery(query: SharedQuery, json: ISerialization, uidAttribute: Attribute<UID>): IndexQuery<*>? =
  when (query) {
    is SharedQuery.Column ->
      attributeByIdent(query.attribute)?.let { attribute ->
        IndexQuery.Column(attribute as Attribute<Any>)
      }
    is SharedQuery.Contains ->
      lookupOne(uidAttribute, query.uid)?.let { eid ->
        attributeByIdent(query.attribute)?.let { attribute ->
          when (query.value) {
            is DurableDbValue.EntityRef -> {
              require(attribute.schema.isRef) {
                "db value is ref but ${query.attribute} is not marked as such in schema!"
              }
              lookupOne(uidAttribute, query.value.entityId)?.let { ref ->
                IndexQuery.Contains(eid, attribute as Attribute<EID>, ref)
              }
            }
            is DurableDbValue.EntityTypeRef -> {
              require(attribute.schema.isRef) {
                "db value is entity type ref but ${query.attribute} is not marked as ref in schema!"
              }
              entityTypeByIdent(query.value.ident)?.let { entityTypeEID ->
                IndexQuery.Contains(eid, attribute as Attribute<EID>, entityTypeEID)
              }
            }
            is DurableDbValue.Scalar -> {
              IndexQuery.Contains(eid, attribute as Attribute<Any>, deserialize(attribute, query.value.json, json))
            }
          }
        }
      }
    is SharedQuery.Entity ->
      lookupOne(uidAttribute, query.uid)?.let { eid ->
        IndexQuery.Entity(eid)
      }
    is SharedQuery.GetMany ->
      lookupOne(uidAttribute, query.uid)?.let { eid ->
        attributeByIdent(query.attribute)?.let { attribute ->
          IndexQuery.GetMany(eid, attribute as Attribute<Any>)
        }
      }
    is SharedQuery.GetOne ->
      lookupOne(uidAttribute, query.uid)?.let { eid ->
        attributeByIdent(query.attribute)?.let { attribute ->
          IndexQuery.GetOne(eid, attribute as Attribute<Any>)
        }
      }
    is SharedQuery.LookupMany ->
      attributeByIdent(query.attribute)?.let { attribute ->
        when (query.value) {
          is DurableDbValue.EntityRef -> {
            require(attribute.schema.isRef) {
              "db value is ref but ${query.attribute} is not marked as such in schema!"
            }
            lookupOne(uidAttribute, query.value.entityId)?.let { ref ->
              IndexQuery.LookupMany(attribute as Attribute<EID>, ref)
            }
          }
          is DurableDbValue.EntityTypeRef -> {
            require(attribute.schema.isRef) {
              "db value is entity type ref but ${query.attribute} is not marked as such in schema!"
            }
            entityTypeByIdent(query.value.ident)?.let { entityTypeEID ->
              IndexQuery.LookupMany(attribute as Attribute<EID>, entityTypeEID)
            }
          }
          is DurableDbValue.Scalar -> {
            require(attribute.schema.indexed || attribute.schema.unique) {
              "db value used in lookup is scalar but ${query.attribute} is not indexed in schema!"
            }
            IndexQuery.LookupMany(attribute as Attribute<Any>, deserialize(attribute, query.value.json, json))
          }
        }
      }
    is SharedQuery.LookupUnique ->
      attributeByIdent(query.attribute)?.let { attribute ->
        require(attribute.schema.unique) {
          "db value used in lookup-unique but ${query.attribute} is not marked unique in schema!"
        }
        when (query.value) {
          is DurableDbValue.EntityRef -> {
            require(attribute.schema.isRef) {
              "db value is ref but ${query.attribute} is not marked as such in schema!"
            }
            lookupOne(uidAttribute, query.value.entityId)?.let { ref ->
              IndexQuery.LookupUnique(attribute as Attribute<EID>, ref)
            }
          }
          is DurableDbValue.EntityTypeRef -> {
            require(attribute.schema.isRef) {
              "db value is ref but ${query.attribute} is not marked as such in schema!"
            }
            entityTypeByIdent(query.value.ident)?.let { entityTypeEID ->
              IndexQuery.LookupUnique(attribute as Attribute<EID>, entityTypeEID)
            }
          }
          is DurableDbValue.Scalar -> {
            IndexQuery.LookupUnique(attribute as Attribute<Any>, deserialize(attribute, query.value.json, json))
          }
        }
      }
    is SharedQuery.RefsTo ->
      lookupOne(uidAttribute, query.uid)?.let { eid ->
        IndexQuery.RefsTo(eid)
      }
  }
