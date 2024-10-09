// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.fastutil.longs

interface LongSet {
  val size: Int
  val values: LongIterator
  operator fun contains(value: Long): Boolean
}