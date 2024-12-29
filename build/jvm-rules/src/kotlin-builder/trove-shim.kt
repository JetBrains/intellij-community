// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package gnu.trove

interface TObjectHashingStrategy<T> : java.io.Serializable {
  fun computeHashCode(`object`: T?): Int
  fun equals(o1: T?, o2: T?): Boolean
}