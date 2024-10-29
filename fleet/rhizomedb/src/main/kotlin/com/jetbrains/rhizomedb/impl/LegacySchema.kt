// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

object LegacySchema {

  object Attr {
    val kProperty: Attribute<KProperty<*>> =
      Attribute.fromEID(eid = 3,
                        schema = Schema(cardinality = Cardinality.Many,
                                        isRef = false,
                                        indexed = true,
                                        unique = true,
                                        cascadeDelete = false,
                                        cascadeDeleteBy = false,
                                        required = false))
  }

  object EntityType {
    val kClass: Attribute<KClass<out Entity>> =
      Attribute.fromEID(eid = 10,
                        schema = Schema(cardinality = Cardinality.One,
                                        isRef = false,
                                        indexed = true,
                                        unique = true,
                                        cascadeDeleteBy = false,
                                        cascadeDelete = false,
                                        required = false))

    val ancestorKClasses: Attribute<KClass<out Entity>> =
      Attribute.fromEID(eid = 11,
                        schema = Schema(cardinality = Cardinality.Many,
                                        isRef = false,
                                        indexed = true,
                                        unique = false,
                                        cascadeDeleteBy = false,
                                        cascadeDelete = false,
                                        required = false))

    val definedAttributes: Attribute<EID> =
      Attribute.fromEID(eid = 14,
                        schema = Schema(cardinality = Cardinality.Many,
                                        isRef = true,
                                        indexed = false,
                                        unique = false,
                                        cascadeDeleteBy = false,
                                        cascadeDelete = false,
                                        required = false))

    val schemaRegistrar: Attribute<SchemaRegistrar> =
      Attribute.fromEID(eid = 15,
                        schema = Schema(cardinality = Cardinality.One,
                                        isRef = false,
                                        indexed = false,
                                        unique = true,
                                        cascadeDelete = false,
                                        cascadeDeleteBy = false,
                                        required = false))
  }

  const val LastMetaSchemaAttrId: Int = 17

  const val TypeIdent: String = "TYPE"
}
