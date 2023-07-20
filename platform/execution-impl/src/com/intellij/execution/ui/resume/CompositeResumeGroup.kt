// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.resume

import com.intellij.execution.ui.RunWidgetManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class CompositeResumeGroup : DefaultActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    e?.project?.let{
      if(RunWidgetManager.getInstance(it).isResumeAvailable()) {
        return arrayOf(CompositeResumeAction())
      }
    }
    return emptyArray()
  }
}