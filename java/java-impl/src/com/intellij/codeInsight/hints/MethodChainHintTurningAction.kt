// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class MethodChainHintTurningAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent?): Boolean {
    return CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE
  }

  override fun setSelected(e: AnActionEvent?, state: Boolean) {
    CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE = state
    MethodChainHintsPassFactory.modificationStampHolder.forceHintsUpdateOnNextPass()
  }

}