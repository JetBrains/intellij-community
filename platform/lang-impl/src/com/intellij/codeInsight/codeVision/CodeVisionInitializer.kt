// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class CodeVisionInitializer(project: Project) {
  companion object {
    fun getInstance(project: Project): CodeVisionInitializer = project.service<CodeVisionInitializer>()
  }

  protected open val host: CodeVisionHost = CodeVisionHost(project)

  open fun getCodeVisionHost(): CodeVisionHost = host

  internal class CodeVisionInitializerStartupActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      withContext(Dispatchers.EDT) {
        getInstance(project).host.initialize()
      }
    }
  }
}