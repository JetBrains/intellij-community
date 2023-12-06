// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer;

import kotlinx.coroutines.CancellationException

/**
 * Key class:
 * - for instances registered in plugin module descriptor: interface class or implementation class if no interface is specified;
 * - for dynamic instances: implementation class.
 */
interface InstanceContainer {

  /**
   * Requests the initialization of instance for [keyClass], and suspends until it's finished.
   *
   * NB Cancellation of the calling coroutine does not cancel the initialization of the instance if it's already started.
   *
   * @return initialized instance
   * @throws InstanceNotRegisteredException if instance for [keyClass] is not registered, and cannot be registered dynamically
   * @throws CancellationException if the calling coroutine is cancelled while suspended
   * @throws Throwable if the initialization is completed with an exception
   */
  suspend fun <T : Any> instance(keyClass: Class<T>): T

  /**
   * If instance initialization was not started: returns `null`.
   * If instance initialization was started, but was not completed: suspends until it's completed.
   * If instance initialization was completed successfully: initialized instance.
   *
   * This function does not try to instantiate the [keyClass] and return it as an instance.
   * @throws InstanceNotRegisteredException if instance for [keyClass] is not registered, and cannot be registered dynamically
   * @throws CancellationException if the calling coroutine is cancelled while suspended
   * @throws Throwable if the initialization is completed with an exception
   */
  suspend fun <T : Any> requestedInstance(keyClass: Class<T>): T?
}
