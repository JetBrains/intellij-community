// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * This class provides access to the **IntelliJ Registry**, a system of internal settings.
 * 
 * See more in the documentation on the [Registry] class.
 */
interface RegistryManager {
  companion object {

    /**
     * Guarantees that the registry has been initialized after the call and is safe to use.
     */
    @JvmStatic
    fun getInstance(): RegistryManager = ApplicationManager.getApplication().service<RegistryManager>()

    /**
     * Guarantees that the registry has been initialized after the call and is safe to use.
     */
    suspend fun getInstanceAsync(): RegistryManager = ApplicationManager.getApplication().serviceAsync()

    @Topic.AppLevel
    @ApiStatus.Internal
    @JvmField
    // only afterValueChanged is dispatched
    val TOPIC: Topic<RegistryValueListener> = Topic(RegistryValueListener::class.java, Topic.BroadcastDirection.NONE, true)
  }

  fun `is`(key: String): Boolean

  fun intValue(key: String): Int

  fun stringValue(key: String): String?

  fun intValue(key: String, defaultValue: Int): Int

  fun get(key: String): RegistryValue

  fun resetValueChangeListener()
}

@ApiStatus.Internal
fun CoroutineScope.useRegistryManagerWhenReadyJavaAdapter(task: (RegistryManager) -> Unit) {
  launch {
    val registryManager = ApplicationManager.getApplication().serviceAsync<RegistryManager>()
    task(registryManager)
  }
}