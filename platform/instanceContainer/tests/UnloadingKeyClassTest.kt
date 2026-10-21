// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.tests

import com.intellij.platform.instanceContainer.internal.DynamicInstanceSupport
import com.intellij.platform.instanceContainer.internal.DynamicInstanceSupport.DynamicInstanceInitializer
import com.intellij.platform.instanceContainer.internal.InstanceContainerImpl
import com.intellij.platform.instanceContainer.internal.InstanceHolder
import com.intellij.platform.instanceContainer.internal.ScopeHolder
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ref.GCUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.lang.ref.WeakReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertNull

class UnloadingKeyClassTest {
  @Test
  fun `a lookup of an unloading key class with no holder does not keep the class`(testInfo: TestInfo): Unit = timeoutRunBlocking {
    ScopeHolder(this, EmptyCoroutineContext, testInfo.displayName).use { holder ->
      InstanceContainerImpl(
        scopeHolder = holder,
        containerName = testInfo.displayName,
        dynamicInstanceSupport = NoDynamicInstances,
        ordered = false,
        shouldTolerateIncorrectOverrides = false,
        isKeyClassUnloading = { it.classLoader is UnloadingClassLoader },
      ).use { container ->
        val classLoaderRef = lookUpUnloadingKeyClass(container)
        GCUtil.tryGcSoftlyReachableObjects { classLoaderRef.get() == null }
        assertNull(classLoaderRef.get(), "The container keeps the class loader of an unloading key class")
      }
    }
  }
}

private fun lookUpUnloadingKeyClass(container: InstanceContainerImpl): WeakReference<ClassLoader> {
  val classLoader = UnloadingClassLoader()
  val keyClass = classLoader.defineCopy(MyServiceImplementation1::class.java)
  assertNull(container.getInstanceHolder(keyClass))
  assertNull(container.getInstanceHolder(keyClass, registerDynamic = true))
  return WeakReference(classLoader)
}

private class UnloadingClassLoader : ClassLoader(UnloadingKeyClassTest::class.java.classLoader) {
  fun defineCopy(aClass: Class<*>): Class<*> {
    val resourceName = aClass.name.replace('.', '/') + ".class"
    val bytes = requireNotNull(parent.getResourceAsStream(resourceName)) { "Cannot find $resourceName" }.use { it.readBytes() }
    return defineClass(aClass.name, bytes, 0, bytes.size)
  }
}

private object NoDynamicInstances : DynamicInstanceSupport {
  override fun dynamicInstanceInitializer(instanceClass: Class<*>): DynamicInstanceInitializer? = null

  override fun dynamicInstanceRegistered(dynamicInstanceHolder: InstanceHolder) {
    error("Must not be invoked")
  }
}
