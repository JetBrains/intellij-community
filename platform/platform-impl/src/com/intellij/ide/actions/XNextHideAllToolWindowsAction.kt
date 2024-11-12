// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionWrapper
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
internal class XNextHideAllToolWindowsAction : AnActionWrapper(HideAllToolWindowsAction()) {
  init {
    templatePresentation.icon = AllIcons.General.FitContent
  }

}