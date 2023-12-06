// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UpdateSession
import com.intellij.openapi.project.DumbAware

class RevealGroup : DefaultActionGroup(), DumbAware {

  override fun postProcessVisibleChildren(visibleChildren: MutableList<out AnAction>, updateSession: UpdateSession): List<AnAction> =
    visibleChildren.map { child ->
      child.apply {
        applyTextOverride(ActionPlaces.REVEAL_IN_POPUP, updateSession.presentation(child))
      }
    }

}
