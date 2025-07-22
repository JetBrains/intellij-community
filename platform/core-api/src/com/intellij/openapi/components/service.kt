// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientKind
import org.jetbrains.annotations.ApiStatus

/**
 * This is primarily intended to be used by the service implementation. When introducing a new service,
 * please add a static `getInstance()` method. For better tooling performance, it is always advised
 * to keep an explicit method return type.
 *
 *     @Service
 *     class MyApplicationService {
 *       companion object {
 *         @JvmStatic
 *         fun getInstance(): MyApplicationService = service()
 *       }
 *     }
 *
 * Using a `getInstance()` method is preferred over a property, because:
 *
 *   - It makes it clearer on the call site that it can involve loading the service, which might not be cheap.
 *
 *   - Loading the service can throw an exception, and having an exception thrown by a method call is less surprising
 *     than if it was caused by property access.
 *
 *   - (Over-)using properties may be error-prone in a way that it might be accidentally changed to a property with an initializer
 *     instead of the correct (but more verbose) property with a getter, and that change can easily be overlooked.
 *
 *   - Using the method instead of a property keeps `MyApplicationService.getInstance()` calls consistent
 *     when used both in Kotlin, and Java.
 *
 *   - Using the method keeps `MyApplicationService.getInstance()` consistent with `MyProjectService.getInstance(project)`,
 *     both on the declaration and call sites.
 *
 * @see ComponentManager.getService
 */
//@RequiresBlockingContext
inline fun <reified T : Any> service(): T {
  val serviceClass = T::class.java
  return ApplicationManager.getApplication().getService(serviceClass)
         ?: throw RuntimeException("Cannot find service ${serviceClass.name} (classloader=${serviceClass.classLoader}, client=${ClientId.currentOrNull})")
}

/**
 * Contrary to [serviceIfCreated], tries to initialize the service if not yet initialized
 */
inline fun <reified T : Any> serviceOrNull(): T? = ApplicationManager.getApplication()?.getService(T::class.java)

/**
 * Contrary to [serviceOrNull], doesn't try to initialize the service if not yet initialized
 */
inline fun <reified T : Any> serviceIfCreated(): T? = ApplicationManager.getApplication()?.getServiceIfCreated(T::class.java)

/**
 * @deprecated Use override accepting {@link ClientKind} for better control over kinds of clients the services are requested for.
 */
@ApiStatus.Experimental
@Deprecated("Use override accepting {@link ClientKind} for better control over kinds of clients the services are requested for")
inline fun <reified T : Any> services(includeLocal: Boolean): List<T> {
  return ApplicationManager.getApplication().getServices(T::class.java, if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
}