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

  /**
   * See `com.jetbrains.rdct.testFramework.launch.ProcessLauncher.GATEWAY_PLUGIN_MODULE`
   */
  const val INTELLIJ_CWM_GUEST_MAIN_MODULE = "intellij.platform.frontend.split.main"
  const val INTELLIJ_IDEA_ULTIMATE_MAIN_MODULE = "intellij.idea.ultimate.main"
  const val GATEWAY_PLUGIN_MODULE = "intellij.gateway.plugin"

  const val DEFAULT_CWM_PASSWORD = "qwerty123"
}