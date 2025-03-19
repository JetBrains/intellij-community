// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

/** [IjentSessionRegistry.register] creates instances of this class. */
class IjentId internal constructor(val id: String) {
  override fun toString(): String = "IjentId($id)"

  override fun equals(other: Any?): Boolean =
    other is IjentId && other.id == id

  override fun hashCode(): Int =
    id.hashCode()
}