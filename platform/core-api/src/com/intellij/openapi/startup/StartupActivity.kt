// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.startup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * ### Obsolescence notice
 * This interface is obsolete in favor of [ProjectActivity].
 *
 * Reasoning: all activities should be executed in the background regardless of smart mode status.
 * It's the responsibility of the implementation to schedule tasks in smart mode, or to dispatch some work to EDT -
 * which can be done via `suspend` in [ProjectActivity.execute] easily.
 */
// [IJPL-90](https://youtrack.jetbrains.com/issue/IJPL-90)
@Obsolete
interface StartupActivity {
  companion object {
    @Internal
    val POST_STARTUP_ACTIVITY: ExtensionPointName<Any> = ExtensionPointName("com.intellij.postStartupActivity")
  }

  fun runActivity(project: Project)

  /**
   * Represents a startup activity that should be executed before [com.intellij.openapi.project.DumbService] switches to the "smart mode".
   */
  interface RequiredForSmartMode : StartupActivity

  /**
   * See **obsolescence notice** on [StartupActivity].
   */
  @Obsolete
  interface DumbAware : StartupActivity, com.intellij.openapi.project.DumbAware

  /**
   * See **obsolescence notice** on [StartupActivity].
   */
  @Deprecated("Use ProjectActivity")
  interface Background : StartupActivity, com.intellij.openapi.project.DumbAware
}

/**
 * Runs an activity after project open.
 * In production mode [execute] gets called inside a coroutine scope spanning from project opening to project closing (or plugin unloading).
 * Flow and any other long-running activities are allowed and natural.
 *
 * Note: in test mode ([com.intellij.openapi.application.Application.isUnitTestMode]) [execute] can be called from an outer coroutine scope.
 * In this case, flow collectors and long-running activities can block project opening and cause timeout.
 *
 * @see StartupManager
 * @see com.intellij.ide.util.RunOnceUtil
 */
@ApiStatus.OverrideOnly
interface ProjectActivity {
  suspend fun execute(project: Project)
}

/**
 * Defines an activity that runs during project initialization.
 *
 * `InitProjectActivity` interfaces must be declared only in the core module and require approval from the Core team before implementation.
 *
 * If [isParallelExecution] is `true`, the activity may run in parallel with other initialization activities.
 */
@Internal
interface InitProjectActivity {
  /**
   * Indicates whether this activity can be executed in parallel with other initialization activities.
   *
   * Defaults to `false`.
   */
  val isParallelExecution: Boolean
    get() = false

  val isEssential: Boolean
    get() = true

  suspend fun run(project: Project)
}

@Internal
abstract class InitProjectActivityJavaShim : InitProjectActivity {
  abstract fun runActivity(project: Project)

  override suspend fun run(project: Project) {
    runActivity(project)
  }
}
