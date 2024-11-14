package com.jetbrains.rhizomedb

import kotlin.jvm.JvmInline

/**
 * Represents attribute in [Datom].
 *
 * [Attribute] itsef is an Entity.
 * [EID] behind it also contains some minimal information about it's [Schema]
 * */
@JvmInline
value class Attribute<T : Any>(private val attr: EID) {
  companion object {
    /**
     * Combines [EID] with [Schema] to build an [Attribute]
     * */
    fun <T : Any> fromEID(eid: EID, schema: Schema): Attribute<T> = Attribute(schema.value.shl(20).or(eid))
  }

  val eid: EID get() = attr
  val schema: Schema get() = Schema(attr.shr(20))
}