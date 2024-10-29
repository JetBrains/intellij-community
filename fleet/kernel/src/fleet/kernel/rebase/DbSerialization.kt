// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.attributeSerializer
import fleet.kernel.*
import fleet.util.serialization.ISerialization
import fleet.util.UID
import kotlinx.serialization.json.JsonElement

fun DbContext<Q>.sharedId(eid: EID, uidAttribute: Attribute<UID>): UID? =
  when {
    partition(eid) == SharedPart ->
      requireNotNull(getOne(eid, uidAttribute)) {
        "${displayEntity(eid)} is in shared partition but has no UID"
      }
    else -> null
  }

internal fun DbContext<Q>.encodeDbValue(uidAttribute: Attribute<UID>,
                                        json: ISerialization,
                                        a: Attribute<*>,
                                        v: Any): DurableDbValue =
  when {
    a.schema.isRef -> {
      when (val typeIdent = getOne(v as EID, EntityType.Ident.attr as Attribute<String>)) {
        null -> {
          val uid = requireNotNull(getOne(v, uidAttribute)) {
            "${displayEntity(v)} has no uid while serializing ${displayAttribute(a)}"
          }
          DurableDbValue.EntityRef(uid)
        }
        else -> DurableDbValue.EntityTypeRef(typeIdent)
      }
    }
    else -> {
      serializeScalar(json, a, v)
    }
  }

internal fun DbContext<Q>.deserialize(
  a: Attribute<*>,
  v: JsonElement,
  serialization: ISerialization
): Any =
  attributeSerializer(a)?.let { serializer ->
    requireNotNull(DbJson.decodeFromJsonElement(serializer, v)) {
      "got null after deserializing value $v of attribute ${displayAttribute(a)}"
    }
  } ?: v

/*
* returns null if and only if v is a reference which can't be resolved
*/
internal fun DbContext<Q>.serialize1(
  json: ISerialization,
  eidToUid: DbContext<Q>.(EID) -> UID?,
  a: Attribute<*>,
  v: Any
): DurableDbValue? =
  when {
    a.schema.isRef ->
      when (val typeIdent = getOne(v as EID, EntityType.Ident.attr as Attribute<String>)) {
        null -> eidToUid(v)?.let { uid -> DurableDbValue.EntityRef(uid) }
        else -> DurableDbValue.EntityTypeRef(typeIdent)
      }
    else -> serializeScalar(json, a, v)
  }

private fun DbContext<Q>.serializeScalar(
  serialization: ISerialization,
  attribute: Attribute<*>,
  value: Any
): DurableDbValue.Scalar =
  when (value) {
    is JsonElement -> DurableDbValue.Scalar(lazyOf(value))
    else -> {
      val serializer = requireNotNull(attributeSerializer(attribute)) {
        "serializer not found for ${displayAttribute(attribute)}"
      }
      DurableDbValue.Scalar(lazy { DbJson.encodeToJsonElement(serializer, value) })
    }
  }

