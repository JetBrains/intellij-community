// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.util.application

internal class RegionSettingsServiceActivity : ApplicationActivity {
  init {
    if (application.isHeadlessEnvironment) throw ExtensionNotApplicableException.create() // no preloading in headless
  }

  override suspend fun execute() {
    RegionSettingsService.getInstanceAsync()
  }
}