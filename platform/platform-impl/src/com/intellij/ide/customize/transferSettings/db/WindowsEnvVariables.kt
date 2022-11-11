// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.RunConfigurationEnvironmentUtils
import java.util.*

object WindowsEnvVariables {
  /*
 * Some widely used env vars.
 */
  val applicationData = get("APPDATA").toString()
  val localApplicationData = get("LOCALAPPDATA").toString()
  val userProfile = get("USERPROFILE").toString()

  /**
   * Function to get one Windows env var.
   *
   * Use without %%. Example: get("APPDATA")
   */
  fun get(variable: String) = System.getenv()[variable] ?: System.getenv()[variable.uppercase(Locale.getDefault())]

  fun expandPath(path: String) = RunConfigurationEnvironmentUtils.expandVariables("%", "%", path, WindowsEnvVariables::get, false, null)
}