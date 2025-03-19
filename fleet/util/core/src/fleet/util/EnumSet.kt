// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

@OptIn(ExperimentalStdlibApi::class)
data class EnumSet<EnumType : Enum<EnumType>>(private val mask: ULong, private val enumEntries: EnumEntries<EnumType>): Set<EnumType> {
  init {
    if (enumEntries.size == 0) {
      throw IllegalArgumentException("EnumSet can't handle enums with 0 entries")
    }
    if (enumEntries.size > ULong.SIZE_BITS) {
      throw IllegalArgumentException("EnumSet can't handle enums with more than ${ULong.SIZE_BITS} entries on this platform")
    }
  }

  override val size: Int
    get() = mask.countOneBits()

  override fun isEmpty(): Boolean {
    return mask == 0UL
  }

  override fun iterator(): Iterator<EnumType> {
    return object : Iterator<EnumType> {
      var iteratorMask = mask

      override fun hasNext(): Boolean {
        return iteratorMask != 0UL
      }

      override fun next(): EnumType {
        if (!hasNext()) {
          throw NoSuchElementException()
        }
        val bitCount = iteratorMask.countTrailingZeroBits()
        iteratorMask -= 1UL shl bitCount
        return enumEntries[bitCount]
      }
    }
  }

  override fun containsAll(elements: Collection<EnumType>): Boolean {
    return if (elements is EnumSet) {
      (elements.mask and mask) == elements.mask
    } else {
      elements.all { contains(it) }
    }
  }

  override fun contains(element: EnumType): Boolean {
    return (mask shr element.ordinal) and 1UL == 1UL
  }

  operator fun plus(element: EnumType): EnumSet<EnumType> {
    return EnumSet(mask or (1UL shl element.ordinal), enumEntries)
  }

  operator fun plus(elements: EnumSet<EnumType>): EnumSet<EnumType> {
    return EnumSet(mask or elements.mask, enumEntries)
  }

  operator fun minus(element: EnumType): EnumSet<EnumType> {
    return EnumSet(mask and (1UL shl element.ordinal).inv(), enumEntries)
  }

  operator fun minus(elements: EnumSet<EnumType>): EnumSet<EnumType> {
    return EnumSet(mask and elements.mask.inv(), enumEntries)
  }

  fun complement(): EnumSet<EnumType> {
    return EnumSet((1UL shl enumEntries.size) - 1UL - mask, enumEntries)
  }

  companion object {
    inline fun <reified EnumType : Enum<EnumType>> allOf(): EnumSet<EnumType> {
      val enumEntries = enumEntries<EnumType>()
      return EnumSet((1UL shl enumEntries.size) - 1UL, enumEntries)
    }

    inline fun <reified EnumType : Enum<EnumType>> noneOf(): EnumSet<EnumType> = EnumSet(0UL, enumEntries<EnumType>())

    inline fun <reified EnumType : Enum<EnumType>> of(value: EnumType) =
      EnumSet(1UL shl value.ordinal, enumEntries<EnumType>())

    inline fun <reified EnumType : Enum<EnumType>> of(v0: EnumType,
                                                      v1: EnumType) = EnumSet(
      (1UL shl v0.ordinal) or (1UL shl v1.ordinal),
      enumEntries<EnumType>()
    )

    inline fun <reified EnumType : Enum<EnumType>> of(v0: EnumType,
                                                      v1: EnumType,
                                                      v2: EnumType) = EnumSet(
      (1UL shl v0.ordinal)
              or (1UL shl v1.ordinal)
              or (1UL shl v2.ordinal),
      enumEntries<EnumType>()
    )

    inline fun <reified EnumType : Enum<EnumType>> of(v0: EnumType,
                                                      v1: EnumType,
                                                      v2: EnumType,
                                                      v3: EnumType) = EnumSet(
      (1UL shl v0.ordinal)
              or (1UL shl v1.ordinal)
              or (1UL shl v2.ordinal)
              or (1UL shl v3.ordinal),
      enumEntries<EnumType>()
    )

    inline fun <reified EnumType : Enum<EnumType>> of(v0: EnumType,
                                                      v1: EnumType,
                                                      v2: EnumType,
                                                      v3: EnumType,
                                                      v4: EnumType) = EnumSet(
      (1UL shl v0.ordinal)
              or (1UL shl v1.ordinal)
              or (1UL shl v2.ordinal)
              or (1UL shl v3.ordinal)
              or (1UL shl v4.ordinal),
      enumEntries<EnumType>()
    )

    inline fun <reified EnumType : Enum<EnumType>> of(v0: EnumType,
                                                      v1: EnumType,
                                                      v2: EnumType,
                                                      v3: EnumType,
                                                      v4: EnumType,
                                                      v5: EnumType) = EnumSet(
      (1UL shl v0.ordinal)
              or (1UL shl v1.ordinal)
              or (1UL shl v2.ordinal)
              or (1UL shl v3.ordinal)
              or (1UL shl v4.ordinal)
              or (1UL shl v5.ordinal),
      enumEntries<EnumType>()
    )

    inline fun <reified EnumType : Enum<EnumType>> of(v0: EnumType, vararg v: EnumType) = EnumSet(
      v.fold(1UL shl v0.ordinal) { acc, value -> acc or (1UL shl value.ordinal) },
      enumEntries<EnumType>()
    )

    inline fun <reified EnumType : Enum<EnumType>> copyOf(values: Collection<EnumType>) = EnumSet(
      values.fold(0UL) { acc, value -> acc or (1UL shl value.ordinal) },
      enumEntries<EnumType>()
    )
  }
}

inline fun <reified EnumType : Enum<EnumType>> enumSetOfAll(): EnumSet<EnumType> = EnumSet.allOf()

inline fun <reified EnumType : Enum<EnumType>> enumSetOfNone(): EnumSet<EnumType> = EnumSet.noneOf()

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(value: EnumType) = EnumSet.of(value)

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(v0: EnumType, v1: EnumType) = EnumSet.of(v0, v1)

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(v0: EnumType, v1: EnumType, v2: EnumType) = EnumSet.of(v0, v1, v2)

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(v0: EnumType, v1: EnumType, v2: EnumType, v3: EnumType) =
  EnumSet.of(v0, v1, v2, v3)

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(v0: EnumType, v1: EnumType, v2: EnumType, v3: EnumType, v4: EnumType) =
  EnumSet.of(v0, v1, v2, v3, v4)

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(v0: EnumType,
                                                         v1: EnumType,
                                                         v2: EnumType,
                                                         v3: EnumType,
                                                         v4: EnumType,
                                                         v5: EnumType) = EnumSet.of(v0, v1, v2, v3, v4, v5)

inline fun <reified EnumType : Enum<EnumType>> enumSetOf(v0: EnumType, vararg v: EnumType) = EnumSet.of(v0, *v)

inline operator fun <reified EnumType : Enum<EnumType>> EnumType.plus(other: EnumType): EnumSet<EnumType> =
  EnumSet.of(this, other)
