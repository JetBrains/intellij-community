// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.ChangeScopeKey
import com.jetbrains.rhizomedb.Entity
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

val Entity.isShared: Boolean
  get() = com.jetbrains.rhizomedb.partition(eid) == SharedPart

/**
 * UGLY HACK for UNSHARED documents, please don't use it
 * */
fun <T> ChangeScope.unshared(f: SharedChangeScope.() -> T): T {
  return SharedChangeScope(this).f()
}

class SharedChangeScope internal constructor(private val changeScope: ChangeScope) : ChangeScope by changeScope

internal interface Shared {
  companion object : ChangeScopeKey<Shared>

  fun <T> shared(f: SharedChangeScope.() -> T): T
}

internal object KeyStack : ChangeScopeKey<PersistentList<Any>>

fun <T> SharedChangeScope.withKey(key: Any, body: SharedChangeScope.() -> T): T = run {
  val oldKey = meta[KeyStack]
  meta[KeyStack] = (oldKey ?: persistentListOf()).add(key)
  val res = body()
  if (oldKey == null) {
    meta.remove(KeyStack)
  }
  else {
    meta[KeyStack] = oldKey
  }
  res
}

/**
 * Designates a shared-partition change.
 * Keep in mind that function [f] must be pure and depend only on [SharedEntity]'s.
 * Local entities will be hidden from [f].
 * [f] will be invoked again, on a different state of the database, if there are any conflicts
 * */
fun <T> ChangeScope.shared(f: SharedChangeScope.() -> T): T =
  requireNotNull(meta[Shared]).shared(f)
