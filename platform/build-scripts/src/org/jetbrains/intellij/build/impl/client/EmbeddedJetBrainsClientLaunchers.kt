// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.client

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.locateIcnsForFrontendMacApp

internal fun getAdditionalEmbeddedClientVmOptions(os: OsFamily, ideContext: BuildContext): List<String> {
  val result = mutableListOf<String>()
  if (os == OsFamily.MACOS && locateIcnsForFrontendMacApp(ideContext) != null) {
    result.add($$"-Dapple.awt.application.icon=$APP_PACKAGE/Contents/Resources/frontend.icns")
  }
  return result
}