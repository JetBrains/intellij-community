// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.controllers

import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

class TransferSettingsSession(private val controller: TransferSettingsController) {
  var isSuccess = false
    private set
  var isFinished = false
    private set

  fun performImport(project: Project, ideVersion: IdeVersion, withPlugins: Boolean, pi: ProgressIndicator) {
    controller.performImport(project, ideVersion, withPlugins, pi)
  }
}