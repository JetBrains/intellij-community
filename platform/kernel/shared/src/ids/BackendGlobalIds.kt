// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.ids

import com.intellij.platform.rpc.Id
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Stores given [value] in the application storage and returns its [Id].
 * Multiple calls of [storeValueGlobally] with the same [value] will return **different** [Id]s.
 *
 * Returned [Id] can be sent through RPC to frontend,
 *   so backend may convert it back later by [findValueById].
 * This API is useful when a frontend needs to refer to some backend object, but this object is not serializable.
 *
 * [value] and associated [Id] will be deleted from the storage when [CoroutineScope] is cancelled.
 * Note: when [storeValueGlobally] is used, given [coroutineScope] won't be finished normally, but can be only cancelled outside.
 *
 * This API should be used only on the backend side.
 * It locates in shared parts, since most parts of the platform is not split yet.
 *
 * Example:
 * ```kotlin
 * // Id locates in shared code
 * @Serializable
 * data class ObjectId(override val uid: UID): Id
 *
 * // Mapping between shared id and the object class which will be stored in application storage
 * object BackendObjectValueIdType<ObjectId, BackendObjectType>: BackendValueIdType(::ObjectId)
 *
 * // Frontend will call this method to acquire reference to a backend object
 * suspend fun rpcCallGetId(): ObjectId {
 *   val backendObject: BackendObjectType = BackendService.getInstance().someObject
 *   val backendObjectId = storeValueGlobally(backendObject.coroutineScope, backendObject, type = BackendObjectValueIdType)
 *   return backendObjectId
 * }
 *
 * // Frontend will use this method to call some code, based on backend object
 * suspend fun rpcCallGetId(id: ObjectId): Response {
 *   val backendObject: BackendObjectType = findValueById(id, type = BackendObjectValueIdType) ?: return Response.Error(...)
 *   // some code which uses backendObject
 * }
 * ```
 *
 * If you need something more powerful than just storing references for the backend objects,
 * it is better to use pure rhizomeDB API.
 *
 * @see BackendValueIdType
 */
@ApiStatus.Internal
fun <TID : Id, Value : Any> storeValueGlobally(
  coroutineScope: CoroutineScope,
  value: Value,
  type: BackendValueIdType<TID, Value>,
): TID {
  return BackendGlobalIdsManager.getInstance().putId(coroutineScope, value, type.idFactory)
}

/**
 * Retrieves a value associated with [BackendValueIdType] by the given [Id].
 * Value will be returned if it was previously stored by [storeValueGlobally], and it wasn't deleted.
 *
 * This API should be used only on the backend side.
 * It locates in shared parts, since most parts of the platform is not split yet.
 *
 * Example:
 * ```kotlin
 * // Id locates in shared code
 * @Serializable
 * data class ObjectId(override val uid: UID): Id
 *
 * // Mapping between shared id and the object class which will be stored in application storage
 * object BackendObjectValueIdType<ObjectId, BackendObjectType>: BackendValueIdType(::ObjectId)
 *
 * // Frontend will call this method to acquire reference to a backend object
 * suspend fun rpcCallGetId(): ObjectId {
 *   val backendObject: BackendObjectType = BackendService.getInstance().someObject
 *   val backendObjectId = storeValueGlobally(backendObject.coroutineScope, backendObject, type = BackendObjectValueIdType)
 *   return backendObjectId
 * }
 *
 * // Frontend will use this method to call some code, based on backend object
 * suspend fun rpcCallGetId(id: ObjectId): Response {
 *   val backendObject: BackendObjectType = findValueById(id, type = BackendObjectValueIdType) ?: return Response.Error(...)
 *   // some code which uses backendObject
 * }
 * ```
 */
@Suppress("unused")
@ApiStatus.Internal
fun <TID : Id, Value : Any> findValueById(id: TID, type: BackendValueIdType<TID, Value>): Value? {
  return BackendGlobalIdsManager.getInstance().findById(id)
}


/**
 *
 * Stores given [value] in the application storage and returns its [Id].
 * Multiple calls of [storeValueGlobally] with the same [value] will return **different** [Id]s.
 *
 * Returned [Id] can be sent through RPC to frontend,
 *   so backend may convert it back later by [findValueById].
 * This API is useful when a frontend needs to refer to some backend object, but this object is not serializable.
 *
 * This is important to call [deleteValueById] when reference is no longer needed/valid!
 * Otherwise, it will be stored in a local in memory storage forever.
 *
 * If you need something more powerful than just storing references for the backend objects,
 * it is better to use pure rhizomeDB API.
 *
 * This API should be used only on the backend side.
 * It locates in shared parts, since most parts of the platform is not split yet.
 *
 * !!! Prefer to use [storeValueGlobally] with [CoroutineScope] as parameter,
 * !!! so it will be deleted properly.
 *
 * @see BackendValueIdType
 */
@ApiStatus.Internal
fun <TID : Id, Value : Any> storeValueGlobally(value: Value, type: BackendValueIdType<TID, Value>): TID {
  return BackendGlobalIdsManager.getInstance().putId(null, value, type.idFactory)
}

/**
 * Deletes the [Id] from backend's in memory storage.
 *
 * Later [findValueById] calls will return [null].
 *
 * This API should be used only on the backend side. It locates in shared parts, since most parts of the platform is not split yet.
 */
@Suppress("unused")
@ApiStatus.Internal
fun <TID : Id, Value : Any> deleteValueById(id: TID, type: BackendValueIdType<TID, Value>) {
  BackendGlobalIdsManager.getInstance().removeId(id)
}
