// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.DbJson
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.Q
import com.jetbrains.rhizomedb.displayAttribute
import com.jetbrains.rhizomedb.displayEntity
import com.jetbrains.rhizomedb.getOne
import com.jetbrains.rhizomedb.impl.attributeSerializer
import com.jetbrains.rhizomedb.partition
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

fun DbContext<Q>.encodeDbValue(
  uidAttribute: Attribute<UID>,
  a: Attribute<*>,
  v: Any,
): DurableDbValue =
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
      serializeScalar(a, v)
    }
  }

fun DbContext<Q>.deserialize(
  a: Attribute<*>,
  v: JsonElement,
): Any =
  attributeSerializer(a)?.let { serializer ->
    requireNotNull(DbJson.decodeFromJsonElement(serializer, v)) {
      "got null after deserializing value $v of attribute ${displayAttribute(a)}"
    }
  } ?: v

/*
* returns null if and only if v is a reference which can't be resolved
*/
fun DbContext<Q>.serialize1(
  eidToUid: DbContext<Q>.(EID) -> UID?,
  a: Attribute<*>,
  v: Any,
): DurableDbValue? =
  when {
    a.schema.isRef ->
      when (val typeIdent = getOne(v as EID, EntityType.Ident.attr as Attribute<String>)) {
        null -> eidToUid(v)?.let { uid -> DurableDbValue.EntityRef(uid) }
        else -> DurableDbValue.EntityTypeRef(typeIdent)
      }
    else -> serializeScalar(a, v)
  }

private fun DbContext<Q>.serializeScalar(
  attribute: Attribute<*>,
  value: Any,
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

