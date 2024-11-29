// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@JvmInline
value class Pattern private constructor(val hash: Long) {
  companion object {

    fun fromHash(hash: Long): Pattern = Pattern(hash)

    fun patternHashes(eid: EID, attr: Attribute<*>, value: Any): LongArray {
      val indexed = attr.schema.indexed || attr.schema.unique
      val ref = attr.schema.isRef
      val many = attr.schema.cardinality == Cardinality.Many
      return when {
        indexed -> {
          longArrayOf(
            patternHash(eid, attr, value),
            patternHash(null, attr, value),
            patternHash(eid, attr, null),
            patternHash(eid, null, null),
            patternHash(null, attr, null))
        }
        ref -> {
          longArrayOf(
            patternHash(eid, attr, value),
            patternHash(null, attr, value),
            patternHash(null, null, value),
            patternHash(eid, attr, null),
            patternHash(eid, null, null),
            patternHash(null, attr, null)
          )
        }
        many -> {
          longArrayOf(
            patternHash(eid, attr, value),
            patternHash(eid, attr, null),
            patternHash(eid, null, null),
            patternHash(null, attr, null))
        }
        else -> {
          longArrayOf(
            patternHash(eid, attr, null),
            patternHash(eid, null, null),
            patternHash(null, attr, null))
        }
      }
    }

    @JvmStatic
    fun pattern(eid: EID?, attr: Attribute<*>?, value: Any?): Pattern =
      Pattern(patternHash(eid, attr, value))

    @JvmStatic
    private fun patternHash(eid: EID?, attr: Attribute<*>?, value: Any?): Long {
      var result = 1L

      var i = 0
      if (eid != null) i++
      if (attr != null) i++
      if (value != null) i++

      val multiplier = when (i) {
        1 -> 1
        2 -> 1572869
        3 -> 1021
        else -> error("impossible")
      }

      if (eid != null) result = multiplier * result + eid
      if (attr != null) result = multiplier * result + attr.eid
      if (value != null) result = multiplier * result + value.hashCode()

      // set pattern type mask in left 3 bits of the hash,
      // each bit corresponds to eid, attrID and value presense
      // thus we can guarantee that there are no collisions for patterns of different types
      result = setBit(eid != null, 1, result)
      result = setBit(attr != null, 2, result)
      result = setBit(value != null, 3, result)

      return result
    }

    private fun setBit(value: Boolean, position: Byte, result: Long): Long {
      return if (value) {
        result.or(1L.shl(Long.SIZE_BITS - position))
      }
      else {
        result.and(1L.shl(Long.SIZE_BITS - position).inv())
      }
    }
  }
}
