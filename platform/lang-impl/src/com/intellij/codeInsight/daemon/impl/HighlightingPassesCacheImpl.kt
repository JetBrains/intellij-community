// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files

class HighlightingPassesCacheImpl(val project: Project, private val scope: CoroutineScope) : HighlightingPassesCache {
  override fun loadPasses(files: List<VirtualFile>) {
    scope.launch {
      val shortFiles = withContext(Dispatchers.IO) {
        files.filter { Files.lines(it.toNioPath()).count() < 3000 }
      }

      val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
      val runner = MainPassesRunner(project, CodeInsightBundle.message("checking.code.highlightings.in.background"), currentProfile)

      ProgressManager.getInstance().runProcess(
        {
          runner.runMainPasses(shortFiles.take(1))
        }, ProgressIndicatorBase())
    }
  }

  companion object {
    fun getInstance(project: Project): HighlightingPassesCacheImpl = project.service()
  }
}