// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object UntilBuildDeprecation {
  private val LOG get() = PluginManagerCore.logger

  private val MINIMAL_API_VERSION = BuildNumber.fromString("252.*")!!

  val forceHonorUntilBuild: Boolean = System.getProperty("idea.plugins.honor.until.build", "true").toBoolean()

  fun nullizeIfTargetsMinimalApiOrLater(untilBuild: String?, diagnosticId: String?): String? {
    if (forceHonorUntilBuild || untilBuild == null) {
      return untilBuild
    }
    try {
      val untilBuildNumber = BuildNumber.fromStringOrNull(untilBuild)
      if (untilBuildNumber != null && untilBuildNumber >= MINIMAL_API_VERSION) {
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