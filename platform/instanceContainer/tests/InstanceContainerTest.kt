// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.tests

import com.intellij.platform.instanceContainer.CycleInitializationException
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import com.intellij.platform.instanceContainer.internal.*
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class InstanceContainerTest {

  @Test
  fun `disposed container`(testInfo: TestInfo): Unit = timeoutRunBlocking {
    val containerName = testInfo.displayName
    ScopeHolder(this, EmptyCoroutineContext, containerName).use { holder ->
      val container = InstanceContainerImpl(holder, containerName, null, false)
      container.dispose()
      assertThrows<ContainerDisposedException> {
        container.instance(MyServiceInterface::class.java)
      }
      assertThrows<ContainerDisposedException> {
        container.requestedInstance(MyServiceInterface::class.java)
      }
      assertThrows<ContainerDisposedException> {
        container.instanceHolders()
      }
      assertThrows<ContainerDisposedException> {
        container.getInstanceHolder(MyServiceInterface::class.java.name)
      }
      assertThrows<ContainerDisposedException> {
        container.getInstanceHolder(MyServiceInterface::class.java)
      }
      assertThrows<ContainerDisposedException> {
        container.getInstanceHolder(MyServiceInterface::class.java, registerDynamic = false)
      }
      assertThrows<ContainerDisposedException> {
        container.getInstanceHolder(MyServiceInterface::class.java, registerDynamic = true)
      }
      assertThrows<ContainerDisposedException> {
        container.startRegistration(CoroutineScope(CoroutineName("")))
      }
      assertThrows<ContainerDisposedException> {
        container.registerInitializer(MyServiceInterface::class.java, ThrowingInitializer, override = false)
      }
      assertThrows<ContainerDisposedException> {
        container.registerInitializer(MyServiceInterface::class.java, ThrowingInitializer, override = true)
      }
      assertThrows<ContainerDisposedException> {
        container.registerInstance(MyServiceInterface::class.java, MyServiceImplementation1())
      }
      assertThrows<ContainerDisposedException> {
        container.replaceInstance(MyServiceInterface::class.java, MyServiceImplementation1())
      }
      assertThrows<ContainerDisposedException> {
        container.replaceInstanceForever(MyServiceInterface::class.java, MyServiceImplementation1())
      }
      assertThrows<ContainerDisposedException> {
        container.unregister(MyServiceInterface::class.java.name)
      }
    }
  }

  @Test
  fun `empty container`(testInfo: TestInfo): Unit = timeoutRunBlocking {
    withContainer(testInfo.displayName) { container ->
      assertNotRegistered(container, MyServiceInterface::class.java)
    }
  }

  @Test
  fun `registration contracts`(testInfo: TestInfo): Unit = timeoutRunBlocking {
    withContainer(testInfo.displayName) { container ->
      assertThrows<IllegalArgumentException> {
        container.startRegistration(CoroutineScope(EmptyCoroutineContext)) // unnamed scope
      }

      val registrar = container.startRegistration(CoroutineScope(CoroutineName("plugin scope")))

      // nothing registered
      assertNull(registrar.complete())

      // cannot use completed registrar
      assertThrows<IllegalStateException> {
        registrar.registerInitializer("", ThrowingInitializer)
      }
    }
  }

  @Test
  fun register(testInfo: TestInfo): Unit = timeoutRunBlocking {
    val keyClass = MyServiceInterface::class.java
    val keyClassName = keyClass.name
    val pluginScope = CoroutineScope(CoroutineName("plugin scope"))
    withContainer(testInfo.displayName) { container ->
      val instance = MyServiceImplementation1()
      val handle = container.startRegistration(pluginScope).run {
        // initial registration - ok
        registerInitializer(keyClassName, ReadyInitializer(instance), override = false)

        // re-registration in the same scope
        assertErrorLogged<InstanceAlreadyRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = false)
        }

        assertNotNull(complete())
      }
      assertEquals(1, container.instanceHolders().size)
      val holder = assertRegistered(container, keyClass, instance, initialized = false)

      container.startRegistration(pluginScope).run {
        // re-registration in a different scope
        assertErrorLogged<InstanceAlreadyRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = false)
        }
        assertNull(complete())
      }
      assertRegistered(container, keyClass, instance, initialized = true)

      val unregistered = handle.unregister().entries.single()
      assertEquals(keyClassName, unregistered.key)
      assertSame(holder, unregistered.value)
    }
  }

  @Test
  fun override(testInfo: TestInfo): Unit = timeoutRunBlocking {
    val keyClass = MyServiceInterface::class.java
    val keyClassName = keyClass.name
    val pluginScope = CoroutineScope(CoroutineName("plugin scope"))
    withContainer(testInfo.displayName) { container ->

      fun InstanceRegistrar.testOverrideNonExistent() {
        assertErrorLogged<InstanceNotRegisteredException> {
          overrideInitializer(keyClassName, ThrowingInitializer)
        }
        assertNull(complete())
      }

      fun InstanceRegistrar.testRemoveNonExistent() {
        assertErrorLogged<InstanceNotRegisteredException> {
          overrideInitializer(keyClassName, null)
        }
        assertNull(complete())
      }

      suspend fun InstanceRegistrar.testOverride(instance: MyServiceInterface) {
        overrideInitializer(keyClassName, ReadyInitializer(instance))
        val handle = assertNotNull(complete())
        val holder = assertRegistered(container, keyClass, instance, initialized = false)
        val unregistered = handle.unregister().entries.single()
        assertSame(keyClassName, unregistered.key)
        assertSame(holder, unregistered.value)
      }

      suspend fun InstanceRegistrar.testRemove() {
        overrideInitializer(keyClassName, null)
        val handle = assertNotNull(complete())
        assertNotRegistered(container, keyClass)
        val unregistered = handle.unregister()
        assertEquals(0, unregistered.size) // TODO consider removing [keyClass → null] mapping
      }

      fun InstanceRegistrar.testRemoveCancellingOut() {
        overrideInitializer(keyClassName, null)
        assertNull(complete())
      }

      val instance1 = MyServiceImplementation1()
      val instance2 = MyServiceImplementation2()

      // override non-existent
      container.startRegistration(pluginScope).run {
        testOverrideNonExistent()
      }
      assertNotRegistered(container, keyClass)

      // remove non-registered
      container.startRegistration(pluginScope).run {
        testRemoveNonExistent()
      }
      assertNotRegistered(container, keyClass)

      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ReadyInitializer(instance1))
        val handle = assertNotNull(complete())
        assertRegistered(container, keyClass, instance1, initialized = false)

        // override registered
        container.startRegistration(pluginScope).run {
          testOverride(instance2)
        }
        assertRegistered(container, keyClass, instance1, initialized = true)

        // override overridden
        container.startRegistration(pluginScope).run {
          overrideInitializer(keyClassName, ThrowingInitializer)
          testOverride(instance2)
        }
        assertRegistered(container, keyClass, instance1, initialized = true)

        // override removed
        container.startRegistration(pluginScope).run {
          overrideInitializer(keyClassName, null)
          testOverride(instance2)
        }
        assertRegistered(container, keyClass, instance1, initialized = true)

        // remove registered
        container.startRegistration(pluginScope).run {
          testRemove()
        }
        assertRegistered(container, keyClass, instance1, initialized = true)

        // remove overridden
        container.startRegistration(pluginScope).run {
          overrideInitializer(keyClassName, ThrowingInitializer)
          testRemove()
        }
        assertRegistered(container, keyClass, instance1, initialized = true)

        // remove removed
        container.startRegistration(pluginScope).run {
          overrideInitializer(keyClassName, null)
          testRemove()
        }
        assertRegistered(container, keyClass, instance1, initialized = true)

        handle.unregister()
      }
      assertNotRegistered(container, keyClass)

      // override registered in the same scope
      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ThrowingInitializer)
        testOverride(instance1)
      }

      // override overridden in the same scope
      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ThrowingInitializer)
        overrideInitializer(keyClassName, ThrowingInitializer)
        testOverride(instance1)
      }

      // override removed in the same scope
      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ThrowingInitializer)
        overrideInitializer(keyClassName, null)
        testOverrideNonExistent()
      }

      // remove registered in the same scope
      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ThrowingInitializer)
        testRemoveCancellingOut()
      }

      // remove overridden in the same scope
      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ThrowingInitializer)
        overrideInitializer(keyClassName, ThrowingInitializer)
        testRemoveCancellingOut()
      }

      // remove removed in the same scope
      container.startRegistration(pluginScope).run {
        registerInitializer(keyClassName, ThrowingInitializer)
        overrideInitializer(keyClassName, null)
        testRemoveNonExistent()
      }
    }
  }

  @Suppress("SelfReferenceConstructorParameter", "UNUSED_PARAMETER")
  private class SelfCycle(a: SelfCycle)

  @Suppress("UNUSED_PARAMETER")
  private class CyclicA(b: CyclicB)

  @Suppress("UNUSED_PARAMETER")
  private class CyclicB(a: CyclicA)

  @Test
  fun `cycle in initialization`(testInfo: TestInfo): Unit = timeoutRunBlocking {
    withContainer(testInfo.displayName) { container ->
      val resolver = TestContainerResolver(container)
      container.startRegistration(CoroutineScope(CoroutineName(""))).run {
        fun register(c: Class<*>) = registerInitializer(c.name, TestClassInstanceInitializer(c, resolver))
        register(SelfCycle::class.java)
        register(CyclicA::class.java)
        register(CyclicB::class.java)
        complete()
      }
      assertThrows<CycleInitializationException> {
        container.instance(SelfCycle::class.java)
      }
      assertThrows<CycleInitializationException> {
        container.instance(CyclicA::class.java)
      }
      assertThrows<CycleInitializationException> {
        container.instance(CyclicB::class.java)
      }
    }
  }

  private class AService

  @Test
  fun `instance is initialized in cancelled container scope`(testInfo: TestInfo): Unit = timeoutRunBlocking {
    val cancelledHolder = ScopeHolder(this, EmptyCoroutineContext, testInfo.displayName).also {
      it.containerScope.cancel()
    }
    InstanceContainerImpl(cancelledHolder, testInfo.displayName, null, ordered = false).use { container ->
      val instance = AService()
      container.registerInitializer(AService::class.java, object : InstanceInitializer {
        override val instanceClassName: String get() = AService::class.java.name
        override fun loadInstanceClass(keyClass: Class<*>?): Class<*> = AService::class.java
        override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
          assertTrue(parentScope.coroutineContext.job.isCancelled)
          yield()
          return instance
        }
      }, override = false)
      assertSame(instance, container.instance(AService::class.java))
    }
  }
}
