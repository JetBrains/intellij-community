// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Provides default auto reload type used in [AutoImportProjectTrackerSettings].
 * This extension point will provide auto reload type for all build systems even
 * if it is implemented in the plugin of the specific language, so it should be
 * implemented with caution.
 */
@ApiStatus.Internal
interface DefaultAutoReloadTypeProvider {
  fun getAutoReloadType(): ExternalSystemProjectTrackerSettings.AutoReloadType

  companion object {
    val EP_NAME: ExtensionPointName<DefaultAutoReloadTypeProvider> = ExtensionPointName
      .create("com.intellij.openapi.externalSystem.autoimport.autoReloadTypeProviderExtension")
  }
}
