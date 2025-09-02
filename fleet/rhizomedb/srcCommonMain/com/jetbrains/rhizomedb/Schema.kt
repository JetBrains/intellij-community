// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import kotlin.jvm.JvmInline

@JvmInline
value class Schema(val value: Int) {
  constructor(cardinality: Cardinality,
              isRef: Boolean,
              indexed: Boolean,
              unique: Boolean,
              cascadeDelete: Boolean,
              cascadeDeleteBy: Boolean,
              required: Boolean) : this(when (cardinality) {
                                          Cardinality.Many -> ManyMask
                                          Cardinality.One -> NothingMask
                                        }
                                          .or(if (isRef) RefMask else NothingMask)
                                          .or(if (indexed) IndexedMask else NothingMask)
                                          .or(if (unique) UniqueMask else NothingMask)
                                          .or(if (cascadeDelete) CascadeDeleteMask else NothingMask)
                                          .or(if (cascadeDeleteBy) CascadeDeleteByMask else NothingMask)
                                          .or(if (required) RequiredMask else NothingMask))

  companion object {
    const val NothingMask: Int = 0
    const val ManyMask: Int = 1
    const val RefMask: Int = 1.shl(1)
    const val IndexedMask: Int = 1.shl(2)
    const val UniqueMask: Int = 1.shl(3)
    const val CascadeDeleteMask: Int = 1.shl(4)
    const val CascadeDeleteByMask: Int = 1.shl(5)
    const val RequiredMask: Int = 1.shl(6)
  }

  val cardinality: Cardinality get() = if (value.and(ManyMask) != NothingMask) Cardinality.Many else Cardinality.One
  val isRef: Boolean get() = value.and(RefMask) != NothingMask
  val indexed: Boolean get() = value.and(IndexedMask) != NothingMask
  val unique: Boolean get() = value.and(UniqueMask) != NothingMask
  val cascadeDelete: Boolean get() = value.and(CascadeDeleteMask) != NothingMask
  val cascadeDeleteBy: Boolean get() = value.and(CascadeDeleteByMask) != NothingMask
  val required: Boolean get() = value.and(RequiredMask) != NothingMask
}

internal fun Schema.validate() {
  if (required && cardinality == Cardinality.Many) {
    error("invalid schema: attribute with Cardinality.Many may not be required")
  }

  if (isRef) {
    if (indexed) {
      error("invalid schema: indexed makes no sense for ref")
    }
  }
  else {
    if (cascadeDelete) {
      error("invalid schema: CascadeDelete makes no sense for non-ref")
    }
    if (cascadeDeleteBy) {
      error("invalid schema: CascadeDeleteBy makes no sense for non-ref")
    }
  }
}

/**
 * Represents cardinality of a particular [Attribute].
 * Every [Attribute] is either multi-valued or no-more-than-single-valued.
 * */
enum class Cardinality {
  One, Many
}
