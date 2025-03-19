package com.intellij.tools.launch.ide.splitMode

internal object IdeConstants {
  /**
   * See `com.intellij.util.PlatformUtils.IDEA_PREFIX` (unavailable from this module).
   */
  const val IDEA_PREFIX = "idea"

  /**
   * See `com.intellij.util.PlatformUtils.JETBRAINS_CLIENT_PREFIX` (unavailable from this module).
   */
  const val JETBRAINS_CLIENT_PREFIX = "JetBrainsClient"

  const val PLATFORM_LOADER_MODULE = "intellij.platform.runtime.loader"
  const val GATEWAY_PLUGIN_MODULE = "intellij.gateway.plugin"

  const val DEFAULT_CWM_PASSWORD = "qwerty123"
}