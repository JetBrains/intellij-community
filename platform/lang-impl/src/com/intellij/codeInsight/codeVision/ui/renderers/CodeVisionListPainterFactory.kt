// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionListPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionTheme
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CodeVisionListPainterFactory {
  companion object {
    @JvmStatic
    fun getInstance(): CodeVisionListPainterFactory = ApplicationManager.getApplication().service()
  }

  fun createCodeVisionListPainter(theme: CodeVisionTheme): CodeVisionListPainter
}

@ApiStatus.Internal
class DefaultCodeVisionListPainterFactory : CodeVisionListPainterFactory {
  override fun createCodeVisionListPainter(theme: CodeVisionTheme): CodeVisionListPainter {
    return CodeVisionListPainter(theme = theme)
  }
}