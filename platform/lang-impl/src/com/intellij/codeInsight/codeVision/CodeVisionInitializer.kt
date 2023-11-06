// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@Deprecated("Access CodeVisionHost as service")
@ApiStatus.ScheduledForRemoval(inVersion = "2024.1")
open class CodeVisionInitializer(project: Project) {
  companion object {
    fun getInstance(project: Project): CodeVisionInitializer = project.service<CodeVisionInitializer>()
  }

  protected open val host: CodeVisionHost = project.service<CodeVisionHost>() // TODO: Don't store as field and initialized later?

  open fun getCodeVisionHost(): CodeVisionHost = host

  internal class CodeVisionInitializerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      val visionInitializer = getInstance(project)
      withContext(Dispatchers.EDT) {
        blockingContext {
          visionInitializer.host.initialize()
        }
      }
    }
  }
}