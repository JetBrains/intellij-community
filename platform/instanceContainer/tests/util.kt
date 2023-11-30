// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.tests

import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import com.intellij.platform.instanceContainer.internal.InstanceContainerImpl
import com.intellij.platform.instanceContainer.internal.InstanceHolder
import com.intellij.platform.instanceContainer.internal.ScopeHolder
import com.intellij.platform.instanceContainer.internal.isStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

internal inline fun ScopeHolder.use(action: (ScopeHolder) -> Unit) {
  try {
    action(this)
  }
  finally {
    containerScope.cancel()
  }
}

internal inline fun InstanceContainerImpl.use(action: (InstanceContainerImpl) -> Unit) {
  try {
    action(this)
  }
  finally {
    dispose()
  }
}

internal suspend fun withContainer(containerName: String, test: suspend CoroutineScope.(InstanceContainerImpl) -> Unit) {
  coroutineScope {
    ScopeHolder(this, EmptyCoroutineContext, containerName).use { holder ->
      InstanceContainerImpl(holder, containerName, null, false).use {
        test(it)
      }
    }
  }
}

internal suspend fun assertNotRegistered(container: InstanceContainerImpl, keyClass: Class<*>) {
  assertThrows<InstanceNotRegisteredException> {
    container.instance(keyClass)
  }
  assertThrows<InstanceNotRegisteredException> {
    container.requestedInstance(keyClass)
  }
  assertTrue(container.instanceHolders().isEmpty())
  assertNull(container.getInstanceHolder(keyClass.name))
  assertNull(container.getInstanceHolder(keyClass))
  assertNull(container.getInstanceHolder(keyClass, registerDynamic = false))
  assertNull(container.getInstanceHolder(keyClass, registerDynamic = true))
}

internal suspend fun <T : Any> assertRegistered(
  container: InstanceContainerImpl,
  keyClass: Class<out T>,
  instance: T,
  initialized: Boolean,
): InstanceHolder {
  val holder = assertStaticHolder(container, keyClass)
  if (!initialized) {
    assertNull(container.requestedInstance(keyClass))
    assertNull(holder.tryGetInstance())
    assertNull(holder.getInstanceIfRequested())
    assertEquals(instance.javaClass.name, holder.instanceClassName())
    assertSame(instance.javaClass, holder.instanceClass())
  }

  assertSame(instance, container.instance(keyClass)) // init

  assertSame(instance, container.requestedInstance(keyClass))
  assertSame(instance, holder.tryGetInstance())
  assertSame(instance, holder.getInstanceIfRequested())
  assertEquals(instance.javaClass.name, holder.instanceClassName())
  assertSame(instance.javaClass, holder.instanceClass())

  return holder
}

private fun assertStaticHolder(container: InstanceContainerImpl, keyClass: Class<*>): InstanceHolder {
  val holder = assertNotNull(container.getInstanceHolder(keyClass.name))
  assertTrue(holder.isStatic())
  assertSame(holder, container.getInstanceHolder(keyClass))
  assertSame(holder, container.getInstanceHolder(keyClass, registerDynamic = false))
  assertSame(holder, container.getInstanceHolder(keyClass, registerDynamic = true))
  return holder
}
