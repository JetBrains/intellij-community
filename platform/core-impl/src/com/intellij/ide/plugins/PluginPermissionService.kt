// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.serviceOrNull
import com.intellij.util.ReflectionUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A request of a plugin for an operation that needs the consent of the user.
 *
 * @param T the type of the object that [PluginPermissionService.withPermission] gives to the action when the user allows the request.
 */
@ApiStatus.Experimental
interface PluginPermissionRequest<T>

/**
 * Asks the user for a permission on behalf of the calling plugin.
 */
@ApiStatus.Experimental
interface PluginPermissionService {
  /**
   * Asks the user to allow [request] and runs [action] when the user allows it.
   *
   * The service finds the calling plugin from the class of the direct caller.
   * Thus, call this function directly from the code of the plugin, and not through a helper in another plugin.
   *
   * @return the result of [action], or a failure with [PluginPermissionNotGrantedException] when the user denies [request].
   * The result is also a failure when the service cannot find the calling plugin or cannot process [request].
   */
  @Throws(PluginPermissionNotGrantedException::class)
  suspend fun <T, R> withPermission(request: PluginPermissionRequest<T>, action: suspend (T) -> R): Result<R>

  companion object {
    fun getInstance(): PluginPermissionService = serviceOrNull<PluginPermissionService>() ?: NoOpPluginPermissionService()
  }
}

/**
 * Gives [PluginPermissionService] to Java code.
 */
@ApiStatus.Experimental
object PluginPermissionJavaShim {
  /**
   * Calls [PluginPermissionService.withPermission] and completes the returned future with the result of [action].
   *
   * The future completes exceptionally with [PluginPermissionNotGrantedException] when the user denies [request].
   */
  @OptIn(DelicateCoroutinesApi::class)
  @JvmStatic
  fun <T, R> withPermission(request: PluginPermissionRequest<T>, action: (T) -> R): CompletableFuture<R> {
    // the service sees the coroutine of this shim as the caller, so the shim gives the Java caller to the service
    val callerClass = ReflectionUtil.getCallerClass(3)
    val context = if (callerClass == null) EmptyCoroutineContext else PluginPermissionJavaCaller(callerClass)
    return GlobalScope.async(context) {
      val result = PluginPermissionService.getInstance().withPermission(request) {
        action(it)
      }
      result.getOrThrow()
    }.asCompletableFuture()
  }
}

/**
 * Holds the class that calls [PluginPermissionJavaShim.withPermission].
 *
 * The service uses this element only when [PluginPermissionJavaShim] is the direct caller of [PluginPermissionService.withPermission].
 * Thus, a plugin cannot use this element to act for another plugin.
 */
@ApiStatus.Internal
class PluginPermissionJavaCaller(val callerClass: Class<*>) : AbstractCoroutineContextElement(PluginPermissionJavaCaller) {
  companion object Key : CoroutineContext.Key<PluginPermissionJavaCaller>
}

/**
 * Tells that the user did not allow a [PluginPermissionRequest].
 */
@ApiStatus.Experimental
class PluginPermissionNotGrantedException(message: String) : RuntimeException(message)

private class NoOpPluginPermissionService : PluginPermissionService {
  override suspend fun <T, R> withPermission(
    request: PluginPermissionRequest<T>,
    action: suspend (T) -> R,
  ): Result<R> {
    return Result.failure(UnsupportedOperationException())
  }
}
