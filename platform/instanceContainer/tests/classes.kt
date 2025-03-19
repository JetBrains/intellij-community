// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.tests

import com.intellij.platform.instanceContainer.instantiation.ArgumentSupplier
import com.intellij.platform.instanceContainer.instantiation.DependencyResolver
import com.intellij.platform.instanceContainer.instantiation.instantiate
import com.intellij.platform.instanceContainer.internal.InstanceContainerInternal
import com.intellij.platform.instanceContainer.internal.InstanceInitializer
import kotlinx.coroutines.CoroutineScope
import java.lang.reflect.Constructor

internal interface MyServiceInterface

internal class MyServiceImplementation1 : MyServiceInterface
internal class MyServiceImplementation2 : MyServiceInterface

internal object ThrowingInitializer : InstanceInitializer {

  override val instanceClassName: String
    get() = "ThrowingInitializer.instanceClassName"

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> {
    error("Must not be invoked")
  }

  override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
    error("Must not be invoked")
  }
}

internal class ReadyInitializer(private val instance: MyServiceInterface) : InstanceInitializer {

  override val instanceClassName: String
    get() = instance.javaClass.name

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> {
    return instance.javaClass
  }

  override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
    return instance
  }
}

internal class TestClassInstanceInitializer(
  private val clazz: Class<*>,
  private val resolver: DependencyResolver,
) : InstanceInitializer {

  override val instanceClassName: String
    get() = clazz.name

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> {
    return clazz
  }

  override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
    return instantiate(resolver, parentScope, instanceClass)
  }
}

internal class TestContainerResolver(private val container: InstanceContainerInternal) : DependencyResolver {

  override fun isApplicable(constructor: Constructor<*>): Boolean {
    return true
  }

  override fun isInjectable(parameterType: Class<*>): Boolean {
    return true
  }

  override fun resolveDependency(parameterType: Class<*>, instanceClass: Class<*>, round: Int): ArgumentSupplier? {
    return container.getInstanceHolder(parameterType)?.let {
      ArgumentSupplier {
        it.getInstance(parameterType)
      }
    }
  }
}
