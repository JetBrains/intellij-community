// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * Marker-interface for 'Close diff editor on ESC' actions.
 *
 * @see DiffVirtualFileBase.createEscapeHandler
 * @see DefaultDiffFileEditorCustomizer
 */
interface DiffEditorEscapeAction

class SimpleDiffEditorEscapeAction(private val escapeHandler: Runnable) : DumbAwareAction(), DiffEditorEscapeAction {
  override fun actionPerformed(e: AnActionEvent) = escapeHandler.run()
}
