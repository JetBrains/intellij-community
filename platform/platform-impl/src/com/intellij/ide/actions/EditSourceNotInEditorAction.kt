// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

internal class EditSourceNotInEditorAction : EditSourceAction() {
  override fun update(e: AnActionEvent) {
    if (e.getData(CommonDataKeys.EDITOR) != null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }
}