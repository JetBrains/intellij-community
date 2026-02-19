// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.AutoPopupControllerHelper
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiElement
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class AutoPopupControllerHelperImpl(
  private val project: Project,
  private val scope: CoroutineScope,
) : AutoPopupControllerHelper {
  override fun autoPopupParameterInfoAfterCompletion(editor: Editor, selectedItem: LookupElement?) {
    if (PlatformUtils.isJetBrainsClient()) {
      triggerAutopopupParameterInfoOnBackend(editor, selectedItem)
    }
    else {
      val highlightedMethod = selectedItem?.`object` as? PsiElement
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, highlightedMethod)
    }
  }

  private fun triggerAutopopupParameterInfoOnBackend(editor: Editor, selectedItem: LookupElement?) {
    scope.launch {
      val rpcId = (selectedItem as? LookupElementWithRpcId)?.rpcId
      AutoPopupControllerRpc.getInstance().autoPopupParameterInfo(editor.editorId(), project.projectId(), rpcId)
    }
  }
}