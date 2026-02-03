// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Hack for Java plugin for talking to backend in rem-dev scenarios.
 * Must be removed once Java plugin is converted to v2.
 */
@ApiStatus.Internal
interface AutoPopupControllerHelper {
  /**
   * Invokes parameter info popup for the given method.
   * In the RemDev scenario, the popup will be scheduled on the backend
   */
  fun autoPopupParameterInfoAfterCompletion(editor: Editor, selectedItem: LookupElement?)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AutoPopupControllerHelper = project.service<AutoPopupControllerHelper>()
  }
}