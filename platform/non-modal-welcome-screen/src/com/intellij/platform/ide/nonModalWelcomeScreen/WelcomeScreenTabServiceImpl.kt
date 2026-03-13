// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenTabService
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTab

internal class WelcomeScreenTabServiceImpl(private val project: Project) : WelcomeScreenTabService {
  override suspend fun openTab() {
    if (!project.isWelcomeExperienceProject()) {
      return
    }
    WelcomeScreenRightTab.show(project)
  }
}
