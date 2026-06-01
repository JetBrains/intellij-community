// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.discoverability

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator

/**
 * Contributes custom properties to the IDE discovery info JSON file (`{pid}-ide-instance.json`).
 *
 * The JSON file is written to the IDE system directory and allows external tools to discover
 * running IDE instances and their capabilities. Each contributor writes its fields
 * to the [JsonGenerator] inside the `"properties"` sub-object of the root JSON object.
 *
 * Implementations that need to update the file dynamically (e.g., on project open/close)
 * should call `DiscoveryService.notifyUpdate` or `DiscoveryService.scheduleNotifyUpdate`.
 */
@ApiStatus.Internal
interface DiscoveryInfoContributor {
  companion object {
    val EP_NAME: ExtensionPointName<DiscoveryInfoContributor> =
      ExtensionPointName("com.intellij.platform.discoveryInfoContributor")
  }

  fun contribute(generator: JsonGenerator)
}
