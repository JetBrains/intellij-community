// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.entity
import com.jetbrains.rhizomedb.get
import com.jetbrains.rhizomedb.partition
import fleet.util.UID
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/*

example which breaks current implementation:

my txs:

1. [a -> b] -- no assumptions, just one instruction moving value from a to b
2. [read(b); b = b + 1] -- assumes b
3. [b -> c]

concurrent tx:
[a++] -- no assumptions, just increments a

assumptions produced by 2. still hold: the last tx modifying b was 1, but the different value was propagated to b via concurrent a++

shared {
  q() -> Assume(query, hash)
  q() -> Assume(query, hash)
  q() -> Assume(query, hash)
  ins
  q()
  ins
  ins
  q()
  ins
}

instruction:: Q -> Data -> Novelty

fun add(e, a v, X, Q) -> Novelty {
  if (q(e, a, v)) emptyList()
  else if ((_, _, v', X') = q(e, a))) {
    listOf(Datom(e, a, v', X' false), Datom(e, a, v, X, true))
  } else {
    listOf(Datom(e, a, v, X, true))
  }
}

Datom: [e a v X]

[e? a? v?]
[e? a? v?]
[e? a? v?]
[e? a? v?]
-> [[e a v X],[e a v X],[e a v X]]

Assume([e? a? v?], X): Instruction

if Xs - read trace of instruction, then
X' = hash(instructionId, hash(Xs))

* */

/**
 * Entity shared between all the clients and workspace
 * */
interface SharedEntity : DurableEntity

val Entity.isShared: Boolean
  get() {
    return partition(eid) == SharedPart
  }

fun KClass<*>.isShared(): Boolean = SharedEntity::class.java.isAssignableFrom(this.java)

@Serializable
data class SharedRef<T : Entity>(val uid: UID) {
  fun derefOrNull(): T? = entity(Durable.Id, uid) as T?
  fun deref(): T = requireNotNull(derefOrNull()) {
    "Entity with uid $uid doesn't exists"
  }
}

fun <T : Entity> T.sharedRef(): SharedRef<T> {
  require(partition(eid) == SharedPart) {
    "SharedRef can be acquired only from entities in shared partition"
  }
  return SharedRef(this[Durable.Id])
}
