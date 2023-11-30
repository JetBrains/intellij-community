// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.ApiStatus

abstract class SaveAndSyncHandler {
  companion object {
    @JvmStatic
    fun getInstance(): SaveAndSyncHandler = ApplicationManager.getApplication().getService(SaveAndSyncHandler::class.java)
  }

  protected val externalChangesModificationTracker: SimpleModificationTracker = SimpleModificationTracker()

  /**
   * If a project is specified - only project settings will be saved.
   * If a project is not specified - app and all project settings will be saved.
   */
  data class SaveTask @JvmOverloads constructor(val project: Project? = null, val forceSavingAllSettings: Boolean = false)

  @ApiStatus.Internal
  abstract fun scheduleSave(task: SaveTask, forceExecuteImmediately: Boolean)

  fun scheduleSave(task: SaveTask) {
    scheduleSave(task, forceExecuteImmediately = false)
  }

  @JvmOverloads
  fun scheduleProjectSave(project: Project, forceSavingAllSettings: Boolean = false) {
    scheduleSave(SaveTask(project, forceSavingAllSettings = forceSavingAllSettings))
  }

  abstract fun scheduleRefresh()

  abstract fun refreshOpenFiles()

  open fun disableAutoSave(): AccessToken = AccessToken.EMPTY_ACCESS_TOKEN

  abstract fun blockSaveOnFrameDeactivation()

  abstract fun unblockSaveOnFrameDeactivation()

  abstract fun blockSyncOnFrameActivation()

  abstract fun unblockSyncOnFrameActivation()

  @ApiStatus.Internal
  abstract fun maybeRefresh(modalityState: ModalityState)

  @ApiStatus.Internal
  abstract fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean

  /**
   * @return a modification tracker incrementing when external commands are likely run.
   *         Currently, it happens on IDE frame deactivation and/or [scheduleRefresh] invocation.
   */
  @ApiStatus.Experimental
  fun getExternalChangesTracker(): ModificationTracker = externalChangesModificationTracker
}
