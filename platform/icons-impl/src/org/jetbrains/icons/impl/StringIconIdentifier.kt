// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.serialization.Serializable
import org.jetbrains.icons.IconIdentifier

@Serializable
class StringIconIdentifier(
  val value: String
): IconIdentifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StringIconIdentifier

    return value == other.value
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }

  override fun toString(): String {
    return value
  }
}