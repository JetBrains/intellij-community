// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.instanceContainer.instantiation.DependencyResolver
import com.intellij.platform.instanceContainer.instantiation.instantiate
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A failed constructor returns no instance to the container, so nothing can dispose what the
 * constructor created. The container therefore cancels the instance scope on a constructor
 * failure. A constructor must anchor its Disposer registrations and subscriptions to the
 * injected scope, for example with [asDisposable], and not to `this`.
 */
class ConstructionFailureCleanupTest {

  private val scopeSignature = listOf(MethodType.methodType(Void.TYPE, CoroutineScope::class.java))

  @Test
  fun `constructor failure cancels the instance scope and disposes scope-anchored registrations`(): Unit = timeoutRunBlocking {
    val parentScope = CoroutineScope(Job())
    try {
      assertThrows<IllegalStateException> {
        instantiate(NoDependenciesResolver, parentScope, ScopeRegisteringFailingService::class.java, scopeSignature)
      }
      val child = ScopeRegisteringFailingService.lastChild!!
      assertTrue(ScopeRegisteringFailingService.lastScope!!.coroutineContext.job.isCancelled)
      assertTrue(child.disposed)
      assertNull(Disposer.getTree().printParentChainToRoot(child))
    }
    finally {
      ScopeRegisteringFailingService.lastChild = null
      ScopeRegisteringFailingService.lastScope = null
      parentScope.cancel()
    }
  }

  @Test
  fun `successful construction keeps scope-anchored registrations until the scope ends`(): Unit = timeoutRunBlocking {
    val parentScope = CoroutineScope(Job())
    try {
      val instance = instantiate(NoDependenciesResolver, parentScope, ScopeRegisteringService::class.java, scopeSignature)
      assertFalse(instance.child.disposed)
      parentScope.cancel()
      assertTrue(instance.child.disposed)
    }
    finally {
      parentScope.cancel()
    }
  }
}

private object NoDependenciesResolver : DependencyResolver {
  override fun isApplicable(constructor: Constructor<*>): Boolean = true
  override fun isInjectable(parameterType: Class<*>): Boolean = false
  override fun resolveDependency(parameterType: Class<*>, instanceClass: Class<*>, round: Int) = null
  override fun toString(): String = "NoDependenciesResolver"
}

private class TrackingDisposable : Disposable {
  @Volatile
  var disposed: Boolean = false

  override fun dispose() {
    disposed = true
  }
}

private class ScopeRegisteringFailingService(scope: CoroutineScope) : Disposable {
  init {
    val child = TrackingDisposable()
    lastChild = child
    lastScope = scope
    Disposer.register(scope.asDisposable(), child)
    error("construction failure")
  }

  override fun dispose() {}

  companion object {
    var lastChild: TrackingDisposable? = null
    var lastScope: CoroutineScope? = null
  }
}

private class ScopeRegisteringService(scope: CoroutineScope) : Disposable {
  val child: TrackingDisposable = TrackingDisposable()

  init {
    Disposer.register(scope.asDisposable(), child)
  }

  override fun dispose() {}
}
