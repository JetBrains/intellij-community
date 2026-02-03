// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.editor.Editor
import com.intellij.util.PsiNavigateUtil

class PsiPointerInlayActionNavigationHandler : InlayActionHandler {
  companion object {
    const val HANDLER_ID: String = "psi.pointer.navigation.handler"
  }

  override fun handleClick(editor: Editor, payload: InlayActionPayload) {
    payload as PsiPointerInlayActionPayload
    PsiNavigateUtil.navigate(payload.pointer.element)
  }
}