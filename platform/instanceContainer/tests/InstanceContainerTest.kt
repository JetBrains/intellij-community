// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.tests

import com.intellij.platform.instanceContainer.CycleInitializationException
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import com.intellij.platform.instanceContainer.internal.*
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

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
  fun registration(testInfo: TestInfo): Unit = timeoutRunBlocking {
    val keyClass = MyServiceInterface::class.java
    val keyClassName = keyClass.name
    val pluginScope = CoroutineScope(CoroutineName("plugin scope"))
    withContainer(testInfo.displayName) { container ->
      val instance1 = MyServiceImplementation1()
      val handle1 = container.startRegistration(pluginScope).run {
        assertThrows<InstanceNotRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = true)
        }
        registerInitializer(keyClassName, ReadyInitializer(instance1), override = false)
        assertThrows<InstanceAlreadyRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = false)
        }
        assertNotNull(complete())
      }
      assertEquals(1, container.instanceHolders().size)
      val holder1 = assertRegistered(container, keyClass, instance1, initialized = false)

      val instance2 = MyServiceImplementation2()
      val handle2 = container.startRegistration(pluginScope).run {
        assertThrows<InstanceAlreadyRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = false)
        }
        registerInitializer(keyClassName, ReadyInitializer(instance2), override = true)
        assertThrows<InstanceAlreadyRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = false)
        }
        assertNotNull(complete())
      }
      assertEquals(1, container.instanceHolders().size)
      val holder2 = assertRegistered(container, keyClass, instance2, initialized = false)

      val handle3 = container.startRegistration(pluginScope).run {
        assertThrows<InstanceAlreadyRegisteredException> {
          registerInitializer(keyClassName, ThrowingInitializer, override = false)
        }
        overrideInitializer(keyClassName, null)
        //assertThrows<InstanceNotRegisteredException> {
        //  registerInitializer(keyClassName, ThrowingInitializer, override = true)
        //}
        assertNotNull(complete())
      }
      assertEquals(0, container.instanceHolders().size)
      assertNotRegistered(container, keyClass)

      assertEquals(0, handle3.unregister().size)

      assertSame(holder2, assertRegistered(container, keyClass, instance2, initialized = true))
      val unregistered2 = handle2.unregister().entries.single()
      assertEquals(keyClassName, unregistered2.key)
      assertSame(holder2, unregistered2.value)

      assertSame(holder1, assertRegistered(container, keyClass, instance1, initialized = true))
      val unregistered1 = handle1.unregister().entries.single()
      assertEquals(keyClassName, unregistered1.key)
      assertSame(holder1, unregistered1.value)
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
