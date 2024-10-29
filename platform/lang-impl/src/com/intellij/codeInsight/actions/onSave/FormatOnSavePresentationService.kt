// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave

import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.openapi.components.service
import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus

/**
 * This service provides per-plugin customizations for grayed hints and context actions
 * for the "Reformat code" and "Optimize imports" actions in the Tools > Actions on Save settings panel.
 *
 * For example, for adding more transparency about which external formatting tools will be used.
 */
@ApiStatus.Internal
open class FormatOnSavePresentationService {
  companion object {
    @JvmStatic
    fun getInstance(): FormatOnSavePresentationService = service()
  }

  open fun getCustomFormatComment(context: ActionOnSaveContext): ActionOnSaveComment? {
    return null
  }

  open fun getCustomImportsComment(context: ActionOnSaveContext): ActionOnSaveComment? {
    return null
  }

  open fun getCustomFormatActionLinks(context: ActionOnSaveContext): List<ActionLink> {
    return emptyList()
  }

  open fun getCustomImportsActionLinks(context: ActionOnSaveContext): List<ActionLink> {
    return emptyList()
  }
}
