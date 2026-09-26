// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import kotlinx.coroutines.CoroutineScope

sealed interface MutableInstanceContainer {

  /**
   * @param registrationScope additional parent scope of registered instances (e.g. plugin scope).
   * Instances will be initialized in an intersection of this container scope and [registrationScope].
   */
  fun startRegistration(registrationScope: CoroutineScope?): InstanceRegistrar

  /**
   * @throws InstanceAlreadyRegisteredException if `override` is `false` and instance was already registered
   * @throws InstanceNotRegisteredException if `override` is `true` but no instance was already registered
   */
  fun registerInitializer(keyClass: Class<*>, initializer: InstanceInitializer, override: Boolean) { // TODO? : UnregisterHandle
    val registrar = startRegistration(registrationScope = null)
    registrar.registerInitializer(keyClassName = keyClass.name, initializer, override)
    registrar.complete()
  }

  /**
   * Registers a prepared instance in the container.
   * @throws InstanceAlreadyRegisteredException if there is a registered instance for [keyClass]
   */
  fun <T : Any> registerInstance(keyClass: Class<T>, instance: T) // TODO? : UnregisterHandle

  /**
   * Registers a prepared [instance] in the container.
   * If another instance is already registered, it's replaced with the new one.
   * @return a handle to undo registration
   */
  fun <T : Any> replaceInstance(keyClass: Class<T>, instance: T): UnregisterHandle

  /**
   * Registers a prepared [instance] in the container.
   * If another instance is already registered, it's replaced with the new one.
   * @return a holder of previously registered instance (for cleaning it up),
   * or `null` if no instance was registered
   */
  fun <T : Any> replaceInstanceForever(keyClass: Class<T>, instance: T): InstanceHolder?

  /**
   * Unregisters statically registered instance
   * @return holder of registered instance (for cleaning it up),
   * or `null` if no instance was registered
   */
  fun unregister(keyClassName: String, unregisterDynamic: Boolean = true): InstanceHolder?
}
