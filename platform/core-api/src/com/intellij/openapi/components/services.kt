// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

/**
 * Initializes the service instance if not yet initialized, and returns the service instance.
 *
 * This is primarily intended to be used by the service implementation. When introducing a new service,
 * please add a static `getInstance(Project)` method. For better tooling performance, it is always advised
 * to keep an explicit method return type.
 * ```kotlin
 * @Service
 * class MyProjectService(private val project: Project) {
 *
 *   companion object {
 *
 *     @JvmStatic
 *     fun getInstance(project: Project): MyProjectService = project.service()
 *   }
 * }
 * ```
 *
 * @throws IllegalStateException if a service cannot be found in [this] component manager
 * @see ComponentManager.getService
 */
inline fun <reified T : Any> ComponentManager.service(): T {
  val serviceClass = T::class.java
  return getService(serviceClass)
         ?: error("Cannot find service ${serviceClass.name} in $this (classloader=${serviceClass.classLoader}")
}

/**
 * Returns `null` if the service cannot be found in [this] component manager,
 * otherwise initializes a service if not yet initialized, and returns the service instance.
 * @see ComponentManager.getService
 */
inline fun <reified T : Any> ComponentManager.serviceOrNull(): T? {
  return getService(T::class.java)
}

/**
 * Returns service instance if it was already initialized, otherwise returns `null`.
 * @see ComponentManager.getServiceIfCreated
 */
inline fun <reified T : Any> ComponentManager.serviceIfCreated(): T? {
  return getServiceIfCreated(T::class.java)
}

/**
 * @see ComponentManager.getServices
 */
inline fun <reified T : Any> ComponentManager.services(includeLocal: Boolean): List<T> {
  return getServices(T::class.java, includeLocal)
}

@ApiStatus.Internal
@ApiStatus.Experimental
suspend inline fun <reified T : Any> ComponentManager.serviceAsync(): Deferred<T> {
  return (this as ComponentManagerEx).getServiceAsync(T::class.java)
}

@ApiStatus.Internal
interface ComponentManagerEx {
  @ApiStatus.Experimental
  @ApiStatus.Internal
  suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): Deferred<T> {
    throw AbstractMethodError()
  }
}