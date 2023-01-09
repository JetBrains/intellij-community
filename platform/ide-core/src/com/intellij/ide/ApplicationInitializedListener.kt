// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

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
   * Invoked when all application level components are initialized.
   * Write actions and time-consuming activities are not recommended because directly affects application start time.
   */
  suspend fun execute(asyncScope: CoroutineScope) {
    @Suppress("DEPRECATION")
    componentsInitialized()
  }

  @Deprecated("Use {@link #execute()}", ReplaceWith("execute()"))
  fun componentsInitialized() {
  }
}