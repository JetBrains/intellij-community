// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.platform.instanceContainer.InstanceContainer
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException

/**
 * Not thread-safe.
 */
sealed interface InstanceRegistrar {

  /**
   * @param keyClassName FQN of a class, which would be later used to obtain the instance via [InstanceContainer.instance]
   * @param initializer instance supplier, it captures all the necessary data;
   * the reference to [initializer] is removed from internal structures after instance initialization
   */
  fun registerInitializer(keyClassName: String, initializer: InstanceInitializer)

  /**
   * @param keyClassName FQN of a class, which would be later used to obtain the instance via [InstanceContainer.instance]
   * @param initializer same as in [registerInitializer]; `null` means remove initializer
   */
  fun overrideInitializer(keyClassName: String, initializer: InstanceInitializer?)

  fun registerInitializer(keyClassName: String, initializer: InstanceInitializer, override: Boolean) {
    if (override) {
      overrideInitializer(keyClassName, initializer)
    }
    else {
      registerInitializer(keyClassName, initializer)
    }
  }

  /**
   * Publishes registered instances in the container.
   * Either all initializers are published, or none of them.
   *
   * @throws InstanceAlreadyRegisteredException if key class name registered via [registerInitializer] was already found in the container
   * @throws InstanceNotRegisteredException if key class name registered via [overrideInitializer] was **not** found in the container
   * @return a handle to undo registration which was done by this function, or `null` if no instances were registered
   */
  fun complete(): UnregisterHandle?
}
