// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.Application
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * **Internal.** Plugins may not use this extension.
 */
@ApiStatus.Internal
interface ApplicationLoadHandler {
  suspend fun beforeApplicationLoaded(event: BeforeApplicationLoadedEvent)
}

@ApiStatus.Internal
class BeforeApplicationLoadedEvent(
  val application: Application,
  val configPath: Path,
  val args: List<String>
)

@ApiStatus.Internal
object ApplicationLoadHandlers {
  @JvmField
  val EP_NAME: ExtensionPointName<ApplicationLoadHandler> = ExtensionPointName("com.intellij.applicationLoadHandler")
}
