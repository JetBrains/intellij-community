// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineCompletionTooltipComponent {
  @Suppress("DialogTitleCapitalization")
  fun create(session: InlineCompletionSession): DialogPanel {
    val panel = panel {
      row {
        cell(shortcutActions(InlineCompletionInsertActionIdResolver.getFor(session.editor))).gap(RightGap.SMALL)
        text(IdeBundle.message("inline.completion.tooltip.shortcuts.accept.description"))

        cell(session.provider.providerPresentation.getTooltip(session.context.editor.project))
      }
    }.apply {
      border = JBUI.Borders.empty(1, 8)
    }
    return panel
  }
}
