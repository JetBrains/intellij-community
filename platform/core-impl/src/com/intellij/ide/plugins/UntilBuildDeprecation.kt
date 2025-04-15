// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.BuildNumber

internal object UntilBuildDeprecation {
  private val LOG get() = PluginManagerCore.logger

  private const val MINIMAL_API_VERSION = 251
  private val forceHonorUntilBuild = System.getProperty("idea.plugins.honor.until.build", "false").toBoolean()

  fun nullizeIfTargets243OrLater(untilBuild: String?, diagnosticId: String?): String? {
    if (forceHonorUntilBuild || untilBuild == null) {
      return untilBuild
    }
    try {
      val untilBuildNumber = BuildNumber.fromStringOrNull(untilBuild)
      if (untilBuildNumber != null && untilBuildNumber.baselineVersion >= MINIMAL_API_VERSION) {
        if (untilBuildNumber < PluginManagerCore.buildNumber) {
          // log only if it would fail the compatibility check without the deprecation in place
          LOG.info("Plugin ${diagnosticId ?: "<no name>"} has until-build set to $untilBuild. " +
                   "Until-build _from plugin configuration file (plugin.xml)_ for plugins targeting ${MINIMAL_API_VERSION}+ is ignored. " +
                   "Effective until-build value can be set via the Marketplace.")
        }
        return null
      }
    }
    catch (e: Throwable) {
      LOG.warn("failed to parse until-build number", e)
    }
    return untilBuild
  }
}