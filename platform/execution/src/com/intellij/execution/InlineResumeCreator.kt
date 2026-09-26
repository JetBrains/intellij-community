// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface InlineResumeCreator {
  companion object {
    private val EP_NAME = ExtensionPointName<InlineResumeCreator>("com.intellij.execution.inlineResumeCreator")
    
    @JvmStatic
    fun getInstance(project: Project): InlineResumeCreator = EP_NAME.extensionList.firstOrNull() ?: DefaultInlineResumeCreator()
  }
  fun getInlineResumeCreator(settings: RunnerAndConfigurationSettings, isWidget: Boolean): AnAction?
}

class DefaultInlineResumeCreator : InlineResumeCreator {
  override fun getInlineResumeCreator(settings: RunnerAndConfigurationSettings, isWidget: Boolean): AnAction? {
    return null
  }
}