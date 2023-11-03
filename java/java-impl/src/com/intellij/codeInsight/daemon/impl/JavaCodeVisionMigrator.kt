// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings.Companion.instance
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class JavaCodeVisionMigrator : ProjectActivity {
  companion object {
    private const val JAVA_CODE_VISION_DEFAULT_POSITION_WAS_SET = "java.code.vision.default.position.was.set"
  }

  override suspend fun execute(project: Project) {
    RunOnceUtil.runOnceForApp(JAVA_CODE_VISION_DEFAULT_POSITION_WAS_SET) {
      instance().defaultPosition = CodeVisionAnchorKind.Right
    }
  }
}