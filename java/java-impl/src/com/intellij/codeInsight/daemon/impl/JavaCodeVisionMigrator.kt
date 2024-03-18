// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.ide.util.runOnceForApp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private const val JAVA_CODE_VISION_DEFAULT_POSITION_WAS_SET = "java.code.vision.default.position.was.set"

private class JavaCodeVisionMigrator : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    runOnceForApp(JAVA_CODE_VISION_DEFAULT_POSITION_WAS_SET) {
      serviceAsync<CodeVisionSettings>().defaultPosition = CodeVisionAnchorKind.Right
    }
  }
}