// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

open class BraceHighlightingCustomizer {
  companion object {
    fun getInstance(project: Project) : BraceHighlightingCustomizer = project.service<BraceHighlightingCustomizer>() 
  }
  
  open fun shouldShowScopeHint(editor: Editor): Boolean = true
}
