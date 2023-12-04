// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.Application
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Third-party plugins must not use this extension.
 */
@ApiStatus.Internal
interface ApplicationLoadListener {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ApplicationLoadListener> = ExtensionPointName("com.intellij.ApplicationLoadListener")
  }

  suspend fun beforeApplicationLoaded(application: Application, configPath: Path)
}
