// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.tooltip.InlineCompletionTooltipFactory
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Allows to customize presentation of tooltip on inline completion hover
 */
interface InlineCompletionProviderPresentation {
  /**
   * See [InlineCompletionTooltipFactory].
   */
  fun getTooltip(project: Project?): JComponent

  companion object {
    fun dummy(provider: InlineCompletionProvider): InlineCompletionProviderPresentation {
      return object : InlineCompletionProviderPresentation {
        override fun getTooltip(project: Project?): JComponent {
          @Suppress("HardCodedStringLiteral")
          return JLabel(provider.id.id)
        }
      }
    }
  }
}