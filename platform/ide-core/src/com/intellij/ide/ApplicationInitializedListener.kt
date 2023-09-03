// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Use extension point `com.intellij.applicationInitializedListener` to register listener.
 * Please note - you cannot use [com.intellij.openapi.extensions.ExtensionPointName.findExtension] for this extension point
 * because this extension point is cleared up after app loading.
 *
 * Not part of [com.intellij.ide.ApplicationLoadListener] to avoid class loading before application initialization.
 */
@ApiStatus.Internal
interface ApplicationInitializedListener {
  /**
   * Invoked when application level is nearly initialized.
   * Write actions and time-consuming activities are forbidden because it directly affects application start time.
   */
  suspend fun execute(asyncScope: CoroutineScope) {
    val aClass = this::class.java
    val message = "Override `execute` (class=$aClass)"
    val classLoader = aClass.classLoader
    if (classLoader is PluginAwareClassLoader) {
      logger<ApplicationInitializedListener>().error(PluginException(message, classLoader.pluginId))
    }
    else {
      logger<ApplicationInitializedListener>().error(message)
    }
    blockingContext {
      @Suppress("DEPRECATION")
      componentsInitialized()
    }
  }

   @Deprecated("Use [execute]", ReplaceWith("execute()"))
   fun componentsInitialized() {
   }
}

@Deprecated("Consider avoiding using of ApplicationInitializedListener", level = DeprecationLevel.ERROR)
abstract class ApplicationInitializedListenerJavaShim : ApplicationInitializedListener {
  final override suspend fun execute(asyncScope: CoroutineScope) {
    blockingContext {
      @Suppress("DEPRECATION")
      componentsInitialized()
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  abstract override fun componentsInitialized()
}