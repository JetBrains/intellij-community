// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

sealed interface InstanceContainerInternal {

  @Throws(ContainerDisposedException::class)
  fun instanceHolders(): Collection<InstanceHolder>

  @Throws(ContainerDisposedException::class)
  fun instanceHoldersAndKeys(): Map<String, InstanceHolder>

  @Throws(ContainerDisposedException::class)
  fun getInstanceHolder(keyClassName: String): InstanceHolder?

  @Throws(ContainerDisposedException::class)
  fun getInstanceHolder(keyClass: Class<*>): InstanceHolder? {
    return getInstanceHolder(keyClassName = keyClass.name)
  }

  /**
   * @param registerDynamic whether to register [keyClass] as a dynamic instance if an instance for [keyClass] is not registered
   */
  @Throws(ContainerDisposedException::class)
  fun getInstanceHolder(keyClass: Class<*>, registerDynamic: Boolean): InstanceHolder?

  @Throws(ContainerDisposedException::class)
  fun dispose()
}
