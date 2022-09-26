// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.controllers

import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.util.*

interface TransferSettingsController {
  fun performImport(project: Project, ideVersion: IdeVersion, withPlugins: Boolean, pi: ProgressIndicator)
  fun performReload(ideVersion: FailedIdeVersion, pi: ProgressIndicator)

  fun addListener(listener: TransferSettingsListener)
}

interface TransferSettingsListener : EventListener {
  fun reloadPerformed(ideVersion: FailedIdeVersion) {}

  fun importStarted(ideVersion: IdeVersion, settings: Settings) {}
  fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {}
  fun importPerformed(ideVersion: IdeVersion, settings: Settings) {}
}