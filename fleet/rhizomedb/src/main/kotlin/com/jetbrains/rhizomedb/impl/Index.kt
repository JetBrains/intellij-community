// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST")

package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*
import fleet.fastutil.ints.firstNotNullOfOrNull
import fleet.fastutil.ints.forEach
import fleet.util.letIf
import kotlin.jvm.JvmInline

private fun avet(attribute: Attribute<*>): Boolean =
  !attribute.schema.isRef && (attribute.schema.indexed || attribute.schema.unique)

private fun vaet(attribute: Attribute<*>): Boolean =
  attribute.schema.isRef

@JvmInline
value class Index private constructor(
  private val partitions: ArrayWithEditor<Partition?>
) {
  companion object {
    fun withPartitions(partitions: Array<Partition?>): Index =
      Index(ArrayWithEditor.withArray(partitions))
  }

  fun remove(editor: Editor, eid: EID, attribute: Attribute<*>, value: Any, sink: (Datom) -> Unit): Index = Index(
    partitions.update(editor, partition(eid)) { partition ->
      requireNotNull(partition)
      val (aevt, tx) = partition.aevt.remove(editor, eid, attribute, value)
      when {
        tx == null -> partition
        else -> {
          sink(Datom(eid, attribute, value, tx, false))
          Partition(
            aevt = aevt,
            avet = partition.avet
              .letIf(avet(attribute)) {
                it.remove(editor, eid, attribute, value)
              },
            vaet = partition.vaet
              .letIf(vaet(attribute)) {
                it.remove(editor, eid, attribute, value as EID)
              }
          )
        }
      }
    })

  fun add(editor: Editor, eid: EID, attribute: Attribute<*>, value: Any, tx: TX, sink: (Datom) -> Unit): Index = Index(
    partitions.update(editor, partition(eid)) { partition ->
      requireNotNull(partition)
      val (aevt, removed, added) = partition.aevt.add(editor, eid, attribute, value, tx)
      when {
        added -> {
          if (removed != null) {
            sink(Datom(eid, attribute, removed.x, removed.tx, false))
          }
          sink(Datom(eid, attribute, value, tx, true))
          val valueToRemove = removed?.x
          Partition(
            aevt = aevt,
            avet = partition.avet
              .letIf(avet(attribute)) { avet ->
                avet
                  .letIf(valueToRemove != null) {
                    it.remove(editor, eid, attribute, valueToRemove!!)
                  }
                  .add(editor, eid, attribute, value, tx)
              },
            vaet = partition.vaet
              .letIf(vaet(attribute)) { vaet ->
                vaet
                  .letIf(valueToRemove != null) {
                    it.remove(editor, eid, attribute, valueToRemove as EID)
                  }
                  .add(editor, eid, attribute, value as EID, tx)
              }
          )
        }
        else -> partition
      }
    }
  )

  private fun <T : Any> column(indexQuery: IndexQuery.Column<T>): List<Datom> =
    buildList {
      indexQuery.partitions.forEach { part ->
        partitions[part]?.aevt?.column(indexQuery.attribute, ::add)
      }
    }

  private fun entity(indexQuery: IndexQuery.Entity): List<Datom> =
    buildList {
      val part = partition(indexQuery.eid)
      val aevt = partitions[part]?.aevt
      aevt
        ?.getOne(indexQuery.eid, Entity.Type.attr as Attribute<EID>)
        ?.let { (entityTypeEid) ->
          val schemaPart = partition(entityTypeEid)
          partitions[schemaPart]!!.aevt
            .getMany(entityTypeEid, EntityType.PossibleAttributes.attr as Attribute<EID>) { datom ->
              aevt.getMany(indexQuery.eid, Attribute(datom.value as EID), ::add)
            }
        }
    }

  private fun <T : Any> getMany(indexQuery: IndexQuery.GetMany<T>): List<Datom> =
    buildList {
      val part = partition(indexQuery.eid)
      partitions[part]?.aevt?.getMany(indexQuery.eid, indexQuery.attribute, ::add)
    }

  private fun <T : Any> getOne(indexQuery: IndexQuery.GetOne<T>): Versioned<T>? = run {
    val part = partition(indexQuery.eid)
    partitions[part]?.aevt?.let { aevt ->
      aevt.getOne(indexQuery.eid, indexQuery.attribute)
        .also { versioned ->
          if (versioned == null && indexQuery.throwIfNoEntity && !aevt.entityExists(indexQuery.eid)) {
            throw EntityDoesNotExistException("entity does not exist ${indexQuery.eid}")
          }
        }
    }
  }

  private fun <T : Any> lookupMany(indexQuery: IndexQuery.LookupMany<T>): List<Datom> =
    buildList {
      indexQuery.partitions.forEach { part ->
        partitions[part]?.let { partition ->
          val attribute = indexQuery.attribute as Attribute<Any>
          val value = indexQuery.value
          when {
            attribute.schema.unique ->
              lookupUnique(
                attribute = attribute,
                value = value,
                vaet = partition.vaet,
                avet = partition.avet
              )
                ?.let { v -> add(Datom(v.eid, attribute, value, v.tx)) }
            vaet(attribute) ->
              partition.vaet.lookupMany(value as EID, attribute, ::add)
            avet(attribute) ->
              partition.avet.lookupMany(attribute, value, ::add)
            else -> error("Can't lookup! Attribute is not indexed, probably you want to add @Indexed")
          }
        }
      }
    }

  private fun <T : Any> lookupUnique(indexQuery: IndexQuery.LookupUnique<T>): VersionedEID? =
    indexQuery.partitions.firstNotNullOfOrNull { part ->
      partitions[part]?.let { partition ->
        lookupUnique(
          attribute = indexQuery.attribute as Attribute<Any>,
          value = indexQuery.value,
          vaet = partition.vaet,
          avet = partition.avet
        )
      }
    }

  private fun refsTo(indexQuery: IndexQuery.RefsTo): List<Datom> =
    buildList {
      indexQuery.partitions.forEach { part ->
        partitions[part]?.vaet?.refsTo(indexQuery.eid, ::add)
      }
    }

  private fun all(indexQuery: IndexQuery.All): Sequence<Datom> =
    sequence {
      indexQuery.partitions.forEach { part ->
        val partition = partitions[part]
        partition?.aevt?.let { aevt ->
          yieldAll(aevt.all())
        }
      }
    }

  private fun <T : Any> contains(indexQuery: IndexQuery.Contains<T>): TX? = run {
    val part = partition(indexQuery.eid)
    val partition = partitions[part]
    partition?.aevt?.contains(indexQuery.eid, indexQuery.attribute, indexQuery.value)
  }

  @Suppress("IMPLICIT_CAST_TO_ANY")
  fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
    when (indexQuery) {
      is IndexQuery.Column<*> -> column(indexQuery as IndexQuery.Column<Any>)
      is IndexQuery.Entity -> entity(indexQuery)
      is IndexQuery.GetMany<*> -> getMany(indexQuery as IndexQuery.GetMany<Any>)
      is IndexQuery.GetOne<*> -> getOne(indexQuery as IndexQuery.GetOne<Any>)
      is IndexQuery.LookupMany<*> -> lookupMany(indexQuery as IndexQuery.LookupMany<Any>)
      is IndexQuery.LookupUnique<*> -> lookupUnique(indexQuery as IndexQuery.LookupUnique<Any>)
      is IndexQuery.RefsTo -> refsTo(indexQuery)
      is IndexQuery.All -> all(indexQuery)
      is IndexQuery.Contains<*> -> contains(indexQuery as IndexQuery.Contains<Any>)
    } as T

  data class Partition internal constructor(
    internal val aevt: AEVT, // contains all datoms
    internal val avet: AVET,
    internal val vaet: VAET,
  ) {
    companion object {
      fun empty(): Partition =
        Partition(
          avet = AVET.empty(Editor()),
          vaet = VAET.empty(),
          aevt = AEVT.empty(Editor())
        )
    }
  }

  fun select(editor: Editor, parts: Set<Int>): Index =
    (1 until partitions.size).fold(this) { index, part ->
      when {
        !parts.contains(part) -> index.setPartition(editor, part, null)
        else -> index
      }
    }

  fun setPartition(editor: Editor, part: Part, partition: Partition?): Index =
    Index(partitions.update(editor, part) { partition })

  fun mergePartitionsFrom(editor: Editor, other: Index): Index = run {
    require(other.partitions.size == partitions.size) {
      "dbs have different number of partitions: $partitions, ${other.partitions} "
    }
    (1 until partitions.size).fold(this) { index, part ->
      other.partitions[part]?.let { partition ->
        index.setPartition(editor, part, partition)
      } ?: index
    }
  }

  fun entityExists(eid: EID): Boolean =
    partitions[partition(eid)]?.aevt?.entityExists(eid) ?: false
}

private fun <T : Any> lookupUnique(attribute: Attribute<T>, value: T, vaet: VAET, avet: AVET): VersionedEID? =
  run {
    require(attribute.schema.unique)
    when {
      vaet(attribute) -> vaet.lookupUnique(value as EID, attribute)
      avet(attribute) -> avet.lookupUnique(attribute, value)
      else -> error("can't lookup!")
    }
  }
