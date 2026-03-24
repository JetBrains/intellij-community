// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator

/**
 * Contributes custom properties to the built-in server instance info JSON file (`{pid}-build-in-server.json`).
 *
 * The JSON file is written to the IDE system directory and allows external tools to discover
 * running IDE instances and their capabilities. Each contributor writes its fields directly
 * to the [JsonGenerator] inside the root JSON object.
 *
 * Implementations that need to update the file dynamically (e.g., on project open/close)
 * should call `BuiltInServerInfoService.notifyUpdate` or `BuiltInServerInfoService.scheduleNotifyUpdate`.
 */
@ApiStatus.Internal
interface BuiltInServerInfoContributor {
  companion object {
    val EP_NAME: ExtensionPointName<BuiltInServerInfoContributor> =
      ExtensionPointName("com.intellij.builtInServerInfoContributor")
  }

  fun contribute(generator: JsonGenerator)
}
