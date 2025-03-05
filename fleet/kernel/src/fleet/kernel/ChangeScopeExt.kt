// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Entity

inline fun <reified E : Entity> ChangeScope.update(entity: E, crossinline f: E.() -> Unit): E? =
  if (entity.exists()) {
    entity.f()
    entity
  }
  else {
    null
  }


inline fun <reified E : Entity> ChangeScope.delete(entity: E): Boolean =
  if (entity.exists()) {
    entity.delete()
    true
  }
  else {
    false
  }

/**
 * [child] entity would be retracted together with [parent]
 */
private data class SharedRetractionRelation(override val eid: EID) : Entity {
  val parent by ParentAttr

  val child by ChildAttr

  companion object : DurableEntityType<SharedRetractionRelation>(SharedRetractionRelation::class, ::SharedRetractionRelation) {
    val ParentAttr = requiredRef<Entity>("parent", RefFlags.CASCADE_DELETE_BY)
    val ChildAttr = optionalRef<Entity>("child", RefFlags.CASCADE_DELETE)
  }
}

private data class RetractionRelation(override val eid: EID) : Entity {
  val parent by ParentAttr

  val child by ChildAttr

  companion object : EntityType<RetractionRelation>(RetractionRelation::class, ::RetractionRelation) {
    val ParentAttr = requiredRef<Entity>("parent", RefFlags.CASCADE_DELETE_BY)
    val ChildAttr = optionalRef<Entity>("child", RefFlags.CASCADE_DELETE)
  }
}

fun ChangeScope.sharedCascadeDelete(parent: Entity, child: Entity?) {
  if (child != null) {
    withDefaultPart(SharedPart) {
      SharedRetractionRelation.new {
        it[SharedRetractionRelation.ParentAttr] = parent
        it[SharedRetractionRelation.ChildAttr] = child
      }
    }
  }
}

fun ChangeScope.cascadeDelete(parent: Entity, child: Entity?) {
  if (child != null) {
    RetractionRelation.new {
      it[RetractionRelation.ParentAttr] = parent
      it[RetractionRelation.ChildAttr] = child
    }
  }
}

internal fun ChangeScope.registerRetractionRelations() {
  register(RetractionRelation)
  register(SharedRetractionRelation)
}