// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.relatedFilesTab

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class RelatedFilesTabAction: ActionGroup(), DumbAware  {
  private val maxLabelCount = 2
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    TODO("Not yet implemented")
  }
}