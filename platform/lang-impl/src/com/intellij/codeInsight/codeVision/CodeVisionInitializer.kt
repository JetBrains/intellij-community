// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.application

open class CodeVisionInitializer(val project: Project) {

  companion object{
    fun getInstance(project: Project) = project.service<CodeVisionInitializer>()
  }

  protected open val host = CodeVisionHost(project)

  open fun getCodeVisionHost() : CodeVisionHost = host

  init {
    application.invokeLater {
      host.initialize()
    }
  }
}