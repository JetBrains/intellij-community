// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus

/**
 * Use extension point `com.intellij.applicationInitializedListener` to register listener.
 * Please note - you cannot use [com.intellij.openapi.extensions.ExtensionPointName.findExtension] for this extension point
 * because this extension point is cleared up after app loading.
 *
 * Not part of [com.intellij.ide.ApplicationLoadListener] to avoid class loading before application initialization.
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@ApiStatus.Internal
interface ApplicationInitializedListener {
  /**
   * Invoked when application level is nearly initialized.
   * Write actions and time-consuming activities are forbidden because it directly affects application start time.
   */
  suspend fun execute() {
    val aClass = this::class.java
    val message = "Override `execute` (class=$aClass)"
    val classLoader = aClass.classLoader
    val log = logger<ApplicationInitializedListener>()
    if (classLoader is PluginAwareClassLoader) {
      if (classLoader.pluginId.idString == "com.jetbrains.rust") {
        log.warn(PluginException(message, classLoader.pluginId))
      }
      else {
        log.error(PluginException(message, classLoader.pluginId))
      }
    }
    else {
      log.error(message)
    }

    @Suppress("DEPRECATION")
    componentsInitialized()
  }

   @Deprecated("Use [execute]", ReplaceWith("execute()"))
   fun componentsInitialized() {
   }
}

@ApiStatus.Internal
@Deprecated("Consider avoiding using of ApplicationInitializedListener", level = DeprecationLevel.ERROR)
abstract class ApplicationInitializedListenerJavaShim : ApplicationInitializedListener {
  final override suspend fun execute() {
    componentsInitialized()
  }

  @Suppress("OVERRIDE_DEPRECATION")
  abstract override fun componentsInitialized()
}