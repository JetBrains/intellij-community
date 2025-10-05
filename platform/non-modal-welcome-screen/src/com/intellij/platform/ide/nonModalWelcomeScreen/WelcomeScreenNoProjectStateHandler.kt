// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.NoProjectStateHandler
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.openapi.wm.ex.getWelcomeScreenProjectProvider

private class WelcomeScreenNoProjectStateHandler : NoProjectStateHandler {
  override fun createHandler(): (suspend () -> Project)? {
    val provider = getWelcomeScreenProjectProvider()
      ?.takeIf { isNonModalWelcomeScreenEnabled && ProjectManager.getInstanceIfCreated()?.openProjects.isNullOrEmpty() } ?: return null
    return {
      WelcomeScreenProjectProvider.createOrOpenWelcomeScreenProject(provider)
    }
  }
}
