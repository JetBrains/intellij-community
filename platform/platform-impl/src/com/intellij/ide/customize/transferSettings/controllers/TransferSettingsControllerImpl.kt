// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.controllers

import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsPerformImportTask
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher

class TransferSettingsControllerImpl : TransferSettingsController {
  private val eventDispatcher = EventDispatcher.create(TransferSettingsListener::class.java)
  override fun performImport(project: Project, ideVersion: IdeVersion, withPlugins: Boolean, pi: ProgressIndicator) {
    eventDispatcher.multicaster.importStarted(ideVersion, ideVersion.settings)
    val performer = ideVersion.provider.getImportPerformer(ideVersion)

    val task = object : TransferSettingsPerformImportTask(project, performer, ideVersion.settings, true) {
      override fun onSuccess() {
        eventDispatcher.multicaster.importPerformed(ideVersion, ideVersion.settings)
      }

      override fun onThrowable(error: Throwable) {
        eventDispatcher.multicaster.importFailed(ideVersion, ideVersion.settings, error)
      }
    }

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, pi)
  }

  override fun performReload(ideVersion: FailedIdeVersion, pi: ProgressIndicator) {
    eventDispatcher.multicaster.reloadPerformed(ideVersion)
  }

  override fun addListener(listener: TransferSettingsListener) {
    eventDispatcher.addListener(listener)
  }
}