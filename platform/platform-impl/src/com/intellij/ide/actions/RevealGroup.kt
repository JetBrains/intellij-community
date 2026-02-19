// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal class RevealGroup : SmartPopupActionGroup(), DumbAware {
  override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
    if (visibleChildren.size > childrenCountThreshold) {
      visibleChildren.forEach {
        it.applyTextOverride(ActionPlaces.REVEAL_IN_POPUP, e.updateSession.presentation(it))
      }
    }
    return super.postProcessVisibleChildren(e, visibleChildren);
  }
}
