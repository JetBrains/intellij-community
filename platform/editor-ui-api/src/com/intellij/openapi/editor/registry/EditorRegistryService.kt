// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the current [EditorRegistry] without a lock.
 */
@ApiStatus.Internal
interface EditorRegistryService {
  fun snapshot(): EditorRegistry

  companion object {
    @Volatile private var instance: EditorRegistryService? = null

    init {
      ApplicationManager.registerCleaner { instance = null }
    }

    /**
     * Until the service is created, returns a fallback that serves [EditorRegistry.DEFAULT].
     */
    internal fun getInstance(): EditorRegistryService {
      instance?.let { return it }
      val application = ApplicationManager.getApplication() ?: return DefaultEditorRegistryService
      val registry = application.serviceIfCreated<EditorRegistryService>() ?: return DefaultEditorRegistryService
      instance = registry
      return registry
    }

    /**
     * Creates the service unless it exists. The creation publishes the first [EditorRegistry].
     */
    @JvmStatic
    fun ensureCreated() {
      instance = ApplicationManager.getApplication().service<EditorRegistryService>()
    }
  }
}

private object DefaultEditorRegistryService : EditorRegistryService {
  override fun snapshot(): EditorRegistry {
    return EditorRegistry.DEFAULT
  }
}
