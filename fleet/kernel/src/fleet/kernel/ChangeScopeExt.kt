// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.shared
import kotlin.reflect.*

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


suspend inline fun <reified E : LegacyEntity, R : Any> getOrCreate(property: KMutableProperty1<E, R>,
                                                             value: R,
                                                             partition: Int? = null,
                                                             noinline f: E.() -> Unit): E {
  return lookupOne(property, value) ?: change { getOrCreate(property, value, partition ?: defaultPart, f) }
}

inline fun <reified E : LegacyEntity, R : Any> ChangeScope.getOrCreate(property: KMutableProperty1<E, R>, value: R,
                                                                 partition: Int = defaultPart,
                                                                 noinline f: E.() -> Unit): E {
  return lookupOne(property, value) ?: new(E::class, partition) { f() }
}

inline fun <reified E : SharedEntity, R : Any> SharedChangeScope.getOrCreate(property: KMutableProperty1<E, R>, value: R,
                                                                             noinline f: E.() -> Unit): E {
  return lookupOne(property, value) ?: new(E::class) { f() }
}

inline fun <reified E : LegacyEntity, R : Any> ChangeScope.delete(uniqueField: KMutableProperty1<E, R>, unique: R): Boolean {
  check(attribute(uniqueField)?.schema?.unique != false) { "the property ${uniqueField} should be unique" }
  return lookupOne(uniqueField, unique)?.delete() != null
}

inline fun <reified E : LegacyEntity, R : Any> ChangeScope.changeOrCreate(uniqueField: KMutableProperty1<E, R>,
                                                                    unique: R,
                                                                    crossinline f: E.(Boolean) -> Unit): E? {
  check(attribute(uniqueField)?.schema?.unique != false) { "the property ${uniqueField} should be unique" }
  if (unique is Entity && !unique.exists()) return null
  return lookupOne(uniqueField, unique)?.apply { f(false) } ?: new(E::class) { uniqueField.set(this, unique); f(true) }
}

inline fun <reified E : SharedEntity, R : Any> SharedChangeScope.changeOrCreate(uniqueField: KMutableProperty1<E, R>,
                                                                                unique: R,
                                                                                crossinline f: E.(Boolean) -> Unit): E? {
  check(attribute(uniqueField)?.schema?.unique != false) { "the property ${uniqueField} should be unique" }
  if (unique is Entity && !unique.exists()) return null
  return lookupOne(uniqueField, unique)?.apply { f(false) } ?: new(E::class) { uniqueField.set(this, unique); f(true) }
}

internal fun ChangeScope.registerRectractionRelations() {
  register(RetractionRelation)
  register(SharedRetractionRelation)
}