// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

/**
 * Run a task only once in the history for a project or application.
 *
 * @author Konstantin Bulenkov
 */
object RunOnceUtil {
  /**
   * Perform the task if it was not performed before for the given project.
   *
   * @param id unique ID for the task
   * @return `true` if the task was performed, `false` if the task had already been performed before.
   */
  @JvmStatic
  fun runOnceForProject(project: Project, id: @NonNls String, task: Runnable): Boolean {
    return doRunOnce(storage = PropertiesComponent.getInstance(project), id = id, ideAware = false, activity = task)
  }

  /**
   * Perform the task if it was not performed before for the given project.
   *
   * @param id unique ID for the task
   * @param isIDEAware if `true`, the task will be performed for each IDE
   * @return `true` if the task was performed, `false` if the task had already been performed before.
   */
  @JvmStatic
  fun runOnceForProject(project: Project, id: @NonNls String, isIDEAware: Boolean, task: Runnable): Boolean {
    return doRunOnce(storage = PropertiesComponent.getInstance(project), id = id, ideAware = isIDEAware, activity = task)
  }

  /**
   * Perform the task if it was not performed before for this application (running IDE instance).
   *
   * @param id unique ID for the task
   * @return `true` if the task was performed, `false` if the task had already been performed before.
   */
  @JvmStatic
  fun runOnceForApp(id: @NonNls String, task: Runnable): Boolean {
    return doRunOnce(storage = PropertiesComponent.getInstance(), id = id, ideAware = false, activity = task)
  }
}

suspend fun runOnceForApp(id: @NonNls String, task: suspend () -> Unit): Boolean {
  val key = createKey(id)
  val storage = serviceAsync<PropertiesComponent>()
  synchronized(storage) {
    if (storage.isTrueValue(key)) {
      return false
    }
    storage.setValue(key, true)
  }
  task()
  return true
}

@Internal
suspend fun runOnceForProject(project: Project, id: @NonNls String, activity: suspend () -> Unit): Boolean {
  val key = createKey(id = id, ideAware = false)
  val storage = PropertiesComponent.getInstance(project)
  if (!storage.updateValue(key, true)) {
    return false
  }

  activity()
  return true
}

private fun doRunOnce(storage: PropertiesComponent, id: @NonNls String, ideAware: Boolean, activity: Runnable): Boolean {
  val key = createKey(id, ideAware)
  synchronized(storage) {
    if (storage.isTrueValue(key)) {
      return false
    }
    storage.setValue(key, true)
  }
  activity.run()
  return true
}

private fun createKey(id: String, ideAware: Boolean): @NonNls String {
  var key = "RunOnceActivity."
  if (ideAware) {
    key += PlatformUtils.getPlatformPrefix() + "."
  }
  return key + id
}
