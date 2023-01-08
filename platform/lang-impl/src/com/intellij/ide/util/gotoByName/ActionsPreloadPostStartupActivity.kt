// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

/**
 * Activity to preload actions in order to avoid waiting on first actions search
 */
class ActionsPreloadPostStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    (ActionManager.getInstance() as? ActionManagerImpl)?.preloadActions(ProgressIndicatorBase())
  }
}