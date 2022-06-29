/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 ******************************************************************************/
package com.intellij.openapi.startup

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * `startupActivity` activity must be defined only by a core and requires approval by core team.
 */
@Internal
interface InitProjectActivity : StartupActivity {
  suspend fun run(project: Project)

  override fun runActivity(project: Project) {
  }
}

@Internal
abstract class InitProjectActivityJavaShim : InitProjectActivity {
  override suspend fun run(project: Project) {
    runActivity(project)
  }
}