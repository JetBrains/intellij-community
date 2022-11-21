// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.startup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Runs an activity on project open.
 *
 * If activity implements [com.intellij.openapi.project.DumbAware], it is executed after a project is opened
 * on a background thread with no visible progress indicator. Otherwise, it is executed on EDT when indexes are ready.
 *
 * See [docs](https://youtrack.jetbrains.com/articles/IDEA-A-219/Startup-Activity#Post-Startup-Activity) for details.
 *
 * @see StartupManager
 *
 * @see com.intellij.ide.util.RunOnceUtil
 */
interface StartupActivity {
  companion object {
    @JvmField
    val POST_STARTUP_ACTIVITY = ExtensionPointName<StartupActivity>("com.intellij.postStartupActivity")
  }

  fun runActivity(project: Project)

  /**
   * Represents a startup activity that should be executed before [com.intellij.openapi.project.DumbService] switches to the "smart mode".
   */
  interface RequiredForSmartMode : StartupActivity

  interface DumbAware : StartupActivity, com.intellij.openapi.project.DumbAware

  interface Background : StartupActivity, com.intellij.openapi.project.DumbAware
}

@ApiStatus.Experimental
interface ProjectPostStartupActivity : StartupActivity {
  suspend fun execute(project: Project)

  override fun runActivity(project: Project) = Unit
}

/**
 * `startupActivity` activity must be defined only by a core and requires approval by core team.
 */
@ApiStatus.Internal
interface InitProjectActivity {
  suspend fun run(project: Project)
}

@ApiStatus.Internal
abstract class InitProjectActivityJavaShim : InitProjectActivity {
  abstract fun runActivity(project: Project)

  override suspend fun run(project: Project) = runActivity(project)
}
