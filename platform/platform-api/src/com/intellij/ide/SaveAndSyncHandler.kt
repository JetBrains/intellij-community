// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    fun getInstance(): SaveAndSyncHandler {
      return ApplicationManager.getApplication().getService(SaveAndSyncHandler::class.java)
    }
  }

  protected val externalChangesModificationTracker = SimpleModificationTracker()

  /**
   * If project is specified - only project settings will be saved.
   * If project is not specified - app and all project settings will be saved.
   */
  data class SaveTask @JvmOverloads constructor(val project: Project? = null, val forceSavingAllSettings: Boolean = false) {
    companion object {
      // for Java clients
      @JvmStatic
      fun projectIncludingAllSettings(project: Project) = SaveTask(project = project, forceSavingAllSettings = true)
    }
  }

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

  @ApiStatus.Experimental
  open fun maybeRefresh(modalityState: ModalityState) {
  }

  @ApiStatus.Internal
  abstract fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean

  /**
   * @return a modification tracker incrementing when external commands are likely run.
   *         Currently it happens on IDE frame deactivation and/or [scheduleRefresh] invocation.
   */
  @ApiStatus.Experimental
  fun getExternalChangesTracker(): ModificationTracker = externalChangesModificationTracker
}
