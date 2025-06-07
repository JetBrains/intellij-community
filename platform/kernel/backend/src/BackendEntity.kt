// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.backend

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.kernel.withKernel
import com.jetbrains.rhizomedb.*
import fleet.kernel.change
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private class BackendValueEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      BackendRhizomeValueEntity
    )
  }
}

/**
 * Creates a new [BackendValueEntity] which holds given [value] and puts it to the local in memory storage.
 * Multiple calls of [newValueEntity] with the same [value] will return **different** ids.
 *
 * Returned [BackendValueEntity.id] can be sent through RPC to frontend,
 *   so backend may convert it back later by [findValueEntity].
 * This API is useful when a frontend needs to refer to some backend object, but this object is not serializable.
 *
 * This is important to call [delete] when reference is no longer needed/valid!
 * Otherwise, it will be stored in a local in memory storage forever.
 *
 * It is possible to make a reference to another [BackendValueEntity] using [cascadeDeleteBy],
 *   so the [BackendValueEntity] will be deleted when another [BackendValueEntity] is deleted.
 *
 * If you need something more powerful than just storing references for the backend objects,
 * it is better to use pure rhizomeDB API.
 *
 * Example:
 * ```kotlin
 * data class SomeId(val id: EID)
 *
 * // Frontend will call this method to acquire reference to a backend object
 * suspend fun rpcCallGetId(): SomeId {
 *   val backendObject: BackendObjectType = BackendService.getInstance().someObject
 *   val backendEntity = newValueEntity(backendObject)
 *   // some logic, how backendEntity should be deleted (e.g. on some service dispose)
 *   return SomeId(backendEntity.id)
 * }
 *
 * // Frontend will use this method to call some code, based on backend object
 * suspend fun rpcCallGetId(someId: SomeId): Response {
 *   val backendEntity = someId.id.findValueEntity<BackendObjectType>() ?: return Response.Error(...)
 *   val backendObject = backendEntity.value
 *   // some code which uses backendObject
 * }
 * ```
 */
@ApiStatus.Internal
suspend fun <T : Any> newValueEntity(value: T): BackendValueEntity<T> {
  var entity: BackendValueEntity<T>? = null
  return try {
    withKernel {
      val rhizomeEntity = change {
        BackendRhizomeValueEntity.new {
          it[BackendRhizomeValueEntity.Value] = value
        }
      }
      @Suppress("UNCHECKED_CAST")
      entity = BackendValueEntity(rhizomeEntity.eid, value, rhizomeEntity as BackendRhizomeValueEntity<T>)
      entity
    }
  }
  catch (e: Exception) {
    entity?.delete()
    throw e
  }
}

/**
 * Retrieves a [BackendValueEntity] by the given [EID] and type [T] of the value which is hold by the entity.
 * [BackendValueEntity] will be returned if it was previously created by [newValueEntity], and it wasn't deleted.
 *
 * Example:
 * ```kotlin
 * data class SomeId(val id: EID)
 *
 * // Frontend will call this method to acquire reference to a backend object
 * suspend fun rpcCallGetId(): SomeId {
 *   val backendObject: BackendObjectType = BackendService.getInstance().someObject
 *   val backendEntity = newValueEntity(backendObject)
 *   // some logic, how backendEntity should be deleted (e.g. on some service dispose)
 *   return SomeId(backendEntity.id)
 * }
 *
 * // Frontend will use this method to call some code, based on backend object
 * suspend fun rpcCallGetId(someId: SomeId): Response {
 *   val backendEntity = someId.id.findValueEntity<BackendObjectType>() ?: return Response.Error(...)
 *   val backendObject = backendEntity.value
 *   // some code which uses backendObject
 * }
 * ```
 */
@ApiStatus.Internal
suspend fun <T : Any> EID.findValueEntity(): BackendValueEntity<T>? {
  return withKernel {
    @Suppress("UNCHECKED_CAST")
    val entity = entity(this@findValueEntity) as? BackendRhizomeValueEntity<T> ?: return@withKernel null
    BackendValueEntity(entity.eid, entity.value, entity)
  }
}

/**
 * Deletes the [BackendValueEntity] from backend's in memory storage.
 *
 * This method is called within a [NonCancellable] section to ensure it completes uninterrupted.
 */
@ApiStatus.Internal
suspend fun <T : Any> BackendValueEntity<T>.delete() {
  withContext(NonCancellable) {
    withKernel {
      change {
        rhizomeEntity.delete()
      }
    }
  }
}

/**
 * Attaches given [BackendValueEntity] to another [BackendValueEntity]s, so when one of the [parentEntities] is deleted,
 *   given one is also deleted.
 *
 * Example:
 * ```kotlin
 * val entity1 = newValueEntity("a")
 * val entity2 = newValueEntity("b")
 * val entity3 = newValueEntity("c").apply {
 *   cascadeDeleteBy(entity1, entity2)
 * }
 * entity1.delete()
 * // entity3 is also deleted here
 * ```
 */
@ApiStatus.Internal
suspend fun <T : Any> BackendValueEntity<T>.cascadeDeleteBy(vararg parentEntities: BackendValueEntity<*>) {
  withKernel {
    val rhizomeEntity = rhizomeEntity
    change {
      for (parentRhizomeEntity in parentEntities.map { it.rhizomeEntity }) {
        rhizomeEntity.add(BackendRhizomeValueEntity.Parents, parentRhizomeEntity)
      }
    }
  }
}

@ApiStatus.Internal
data class BackendValueEntity<T>(
  val id: EID,
  val value: T,
  val rhizomeEntity: BackendRhizomeValueEntity<T>,
)

@ApiStatus.Internal
data class BackendRhizomeValueEntity<T>(override val eid: EID) : Entity {
  private val parents by Parents

  @Suppress("UNCHECKED_CAST")
  val value: T
    get() = this[Value] as T

  @ApiStatus.Internal
  companion object : EntityType<BackendRhizomeValueEntity<*>>(
    BackendRhizomeValueEntity::class.java.name,
    "com.intellij",
    { BackendRhizomeValueEntity<Any>(it) }
  ) {
    val Parents = manyRef<BackendRhizomeValueEntity<*>>("parents", RefFlags.CASCADE_DELETE_BY)
    val Value = requiredTransient<Any>("value")
  }
}