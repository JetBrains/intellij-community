// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.Cardinality
import fleet.kernel.rete.Query

internal class InternedQuery<C : Cardinality, T>(
  val key: Any,
  val query: Query<C, T>,
) : Query<C, T> by query {
  override fun hashCode(): Int = key.hashCode() + 1
  override fun equals(other: Any?): Boolean = other is InternedQuery<*, *> && other.key == key
  override fun toString(): String = "InternedQuery($key, $query)"
}

