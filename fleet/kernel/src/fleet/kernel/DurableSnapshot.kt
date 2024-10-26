// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Schema.Companion.CascadeDeleteByMask
import com.jetbrains.rhizomedb.Schema.Companion.IndexedMask
import com.jetbrains.rhizomedb.Schema.Companion.NothingMask
import com.jetbrains.rhizomedb.Schema.Companion.RefMask
import com.jetbrains.rhizomedb.Schema.Companion.RequiredMask
import com.jetbrains.rhizomedb.Schema.Companion.UniqueMask
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.kernel.rebase.deserialize
import fleet.kernel.rebase.encodeDbValue
import fleet.tracing.span
import fleet.util.UID
import fleet.util.logging.logger
import fleet.util.serialization.ISerialization
import fleet.util.serialization.withSerializationCallback
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.reflect.KClass

@Serializable
data class DurableSnapshot(val entities: List<DurableEntity>) {
  companion object {
    val Empty: DurableSnapshot = DurableSnapshot(emptyList())
  }

  @Serializable
  data class Attr(val ident: String, val schema: Int)

  @Serializable
  sealed class OneOrMany {
    @Serializable
    data class One(val value: VersionedValue) : OneOrMany()

    @Serializable
    data class Many(val values: List<VersionedValue>) : OneOrMany()
  }

  @Serializable
  data class VersionedValue(
    val value: DurableDbValue,
    val tx: Long,
  )

  @Serializable
  data class DurableEntity(
    val uid: UID,
    val attrs: Map<@Serializable(with = AttrMigratingSerializer::class) Attr, OneOrMany>,
  )

  /**
   * Serializer fixes invariants for attribute flags that have been violated in the previous versions.
   *
   * 1. Previously it was possible to mark attributes Indexed and Unique at the same time.
   *    Now it is not possible, which makes old stored attributes incompatible with the new scheme.
   *    To fix this, this serializer resets the Indexed flag for Unique attribute from the snapshot.
   *
   * 2. Previously it was possible to create reference required attributes without cascadeDeleteBy flag.
   *    Now it is not possible, which makes old stored attributes incompatible with the new scheme.
   *    To fix this, this serializer adds CascadeDeleteBy flag for required reference attributes
   */
  object AttrMigratingSerializer : KSerializer<Attr> {
    private val originSerializer = Attr.serializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = SerialDescriptor("Attr", originSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: Attr) {
      encoder.encodeSerializableValue(originSerializer, value)
    }

    override fun deserialize(decoder: Decoder): Attr {
      val attr = decoder.decodeSerializableValue(originSerializer)
      return removeRedundantIndexedFlag(addImplicitCascadeDeleteByFlag(attr))
    }

    private fun addImplicitCascadeDeleteByFlag(attr: Attr): Attr {
      return if (attr.schema.and(RefMask) != NothingMask &&
                 attr.schema.and(RequiredMask) != NothingMask &&
                 attr.schema.and(CascadeDeleteByMask) == NothingMask) {
        attr.copy(schema = attr.schema.or(CascadeDeleteByMask))
      }
      else {
        attr
      }
    }

    private fun removeRedundantIndexedFlag(attr: Attr): Attr {
      return if (attr.schema.and(UniqueMask) != NothingMask && attr.schema.and(IndexedMask) != NothingMask) {
        attr.copy(schema = attr.schema.and(IndexedMask.inv()))
      }
      else {
        attr
      }
    }
  }
}


fun DbContext<Q>.buildDurableSnapshot(
  datoms: Sequence<Datom>,
  json: ISerialization,
  serializationRestrictions: Set<KClass<*>>,
): DurableSnapshot {
  val uidAttribute = uidAttribute()
  val entities: MutableMap<UID, MutableMap<DurableSnapshot.Attr, DurableSnapshot.OneOrMany>> = hashMapOf()
  var curDatom: Datom? = null
  withSerializationCallback(callback = { value ->
    if (serializationRestrictions.contains(value::class)) {
      logger<DurableSnapshot>().error {
        "it's not allowed to serialize `$value` while preparing durable snapshot for datom: ${displayDatom(curDatom!!)}"
      }
    }
  }) {
    datoms.filter { it.attr != Entity.EntityObject.attr }.forEach { datom ->
      val (e, a, v, t) = datom
      curDatom = datom
      val uid = requireNotNull(getOne(e, uidAttribute)) { "datom is not durable: ${displayDatom(datom)}" }
      entities.compute(uid) { _: UID, m: MutableMap<DurableSnapshot.Attr, DurableSnapshot.OneOrMany>? ->
        val map = (m ?: hashMapOf())
        val attr = DurableSnapshot.Attr(attributeIdent(a)!!, a.schema.value)
        when (a.schema.cardinality) {
          Cardinality.One -> {
            map[attr] = DurableSnapshot.OneOrMany.One(DurableSnapshot.VersionedValue(encodeDbValue(uidAttribute, json, a, v), t))
          }
          Cardinality.Many -> {
            map.compute(attr) { _, existingValue ->
              existingValue as DurableSnapshot.OneOrMany.Many?
              val value = encodeDbValue(uidAttribute, json, a, v)
              if (existingValue != null) {
                DurableSnapshot.OneOrMany.Many(existingValue.values + DurableSnapshot.VersionedValue(value, t))
              }
              else {
                DurableSnapshot.OneOrMany.Many(listOf(DurableSnapshot.VersionedValue(value, t)))
              }
            }
          }
        }
        map
      }
    }
  }
  return DurableSnapshot(entities = entities.map { DurableSnapshot.DurableEntity(it.key, it.value) })
}

internal fun DbContext<Mut>.applySnapshot(snapshot: DurableSnapshot, json: ISerialization, uidToEid: (UID) -> EID) {
  data class MyEntity(
    val entityTypeEid: EID,
    val id: UID,
    val attrs: List<Pair<Attribute<*>, DurableSnapshot.VersionedValue>>,
  )

  span("applySnapshot", { set("entitiesNum", snapshot.entities.size.toString()) }) {
    val typeAttr = DurableSnapshot.Attr(Entity.Type.ident, Entity.Type.attr.schema.value)
    val legacyTypeAttr = DurableSnapshot.Attr("TYPE", Entity.Type.attr.schema.value)
    val entities: List<MyEntity> = snapshot.entities.map { entity ->
      val attrs = entity.attrs.flatMap { (attr, oneOrMany) ->
        val (ident, schema) = attr
        val attribute = when {
          ident == "TYPE" -> Entity.Type.attr
          else -> {
            attributeByIdent(ident) ?: run {
              val createAttribute = createUnknownAttribute(ident, Schema(schema), 0L)
              mutate(createAttribute)
              Attribute(createAttribute.eid)
            }
          }
        }
        when (oneOrMany) {
          is DurableSnapshot.OneOrMany.Many -> oneOrMany.values.map { attribute to it }
          is DurableSnapshot.OneOrMany.One -> listOf(attribute to oneOrMany.value)
        }
      }

      val type = requireNotNull(entity.attrs[typeAttr] ?: entity.attrs[legacyTypeAttr]) {
        "entity $entity has no type attribute"
      } as DurableSnapshot.OneOrMany.One
      val typeIdent = (type.value.value as DurableDbValue.EntityTypeRef).ident

      val entityTypeEid = run {
        val createEntityType = createUnknownEntityType(
          ident = typeIdent,
          attrs = attrs.map { it.first } + Durable.attrs,
          seed = 0L
        )
        mutate(createEntityType)
        createEntityType.eid
      }

      MyEntity(
        entityTypeEid = entityTypeEid,
        id = entity.uid,
        attrs = attrs
      )
    }

    val instruction = Instruction.Const(
      seed = 0L,
      effects = emptyList(),
      result = entities.flatMap { entity ->
        val eid = uidToEid(entity.id)
        entity.attrs.map { (attribute, versionedValue) ->
          val value = when (val value = versionedValue.value) {
            is DurableDbValue.EntityRef ->
              uidToEid(value.entityId)
            is DurableDbValue.EntityTypeRef ->
              requireNotNull(entityTypeByIdent(value.ident)) { "entity type should have already been registered" }
            is DurableDbValue.Scalar ->
              kotlin.runCatching {
                deserialize(attribute, value.json, json)
              }.getOrElse { x ->
                DeserializationProblem.Exception(throwable = x,
                                                 datom = Datom(eid, attribute, value.json, versionedValue.tx))
              }
          }
          Op.AssertWithTX(eid, attribute, value, versionedValue.tx)
        }
      })
    mutate(instruction)
    entities.map { it.entityTypeEid }.toSet().forEach { etype ->
      mutate(ReifyEntities(etype, generateSeed()))
    }
  }
}