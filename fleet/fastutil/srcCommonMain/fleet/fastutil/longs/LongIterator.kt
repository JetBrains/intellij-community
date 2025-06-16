// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

interface LongIterator {
  fun next(): Long
  fun hasNext(): Boolean
}