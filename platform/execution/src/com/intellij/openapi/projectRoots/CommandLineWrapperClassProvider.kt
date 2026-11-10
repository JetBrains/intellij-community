// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Gives [JdkCommandLineSetup] the `com.intellij.rt.execution.CommandLineWrapper` class for the classpath shortening.
 * The class is in `idea_rt.jar`, and only the plugin that ships the jar can load it.
 */
@ApiStatus.Internal
interface CommandLineWrapperClassProvider {
  val commandLineWrapperClass: Class<*>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<CommandLineWrapperClassProvider> =
      ExtensionPointName("com.intellij.execution.commandLineWrapperClassProvider")
  }
}
