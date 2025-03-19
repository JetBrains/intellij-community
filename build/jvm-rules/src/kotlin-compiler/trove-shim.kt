// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package gnu.trove

@Suppress("unused")
internal interface TObjectHashingStrategy<T> : java.io.Serializable {
  fun computeHashCode(`object`: T?): Int
  fun equals(o1: T?, o2: T?): Boolean
}