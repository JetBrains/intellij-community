// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class DocumentationForwardAction : AnAction(), ActionToIgnore {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = documentationHistory(e.dataContext)?.canForward() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    documentationHistory(e.dataContext)?.forward()
  }
}
