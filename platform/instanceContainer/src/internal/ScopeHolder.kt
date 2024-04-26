// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class ScopeHolder(
  parentScope: CoroutineScope,
  additionalContext: CoroutineContext,
  private val containerName: String,
) {

  val containerScope: CoroutineScope = parentScope.namedChildScope("$containerName container", additionalContext)

  /**
   * Key: plugin coroutine scope.
   * Value: intersection of [containerScope] and plugin coroutine scope.
   */
  private val _registeredScopes = AtomicReference<PersistentMap<CoroutineScope, CoroutineScope>>(persistentHashMapOf())

  fun intersectScope(pluginScope: CoroutineScope?): CoroutineScope {
    if (pluginScope == null) {
      return containerScope // no intersection
    }
    var scopes = _registeredScopes.get()
    scopes[pluginScope]?.let {
      return it
    }
    val intersectionName = "($containerName x ${pluginScope.coroutineContext[CoroutineName]?.name})"
    val intersectionScope = containerScope.namedChildScope(intersectionName).also {
      it.attachAsChildTo(pluginScope)
    }
    while (true) {
      val newScopes = scopes.put(pluginScope, intersectionScope)
      val witness = _registeredScopes.compareAndExchange(scopes, newScopes)
      if (witness === scopes) {
        intersectionScope.coroutineContext.job.invokeOnCompletion {
          unregisterScope(pluginScope)
        }
        // published successfully
        return intersectionScope
      }
      witness[pluginScope]?.let {
        // another thread published the scope
        // => use the value from another thread, and cancel the unpublished scope
        intersectionScope.cancel()
        return it
      }
      // try to publish again
      scopes = witness
    }
  }

  private fun unregisterScope(scope: CoroutineScope) {
    _registeredScopes.updateAndGet { scopes ->
      scopes.remove(scope)
    }
  }
}
