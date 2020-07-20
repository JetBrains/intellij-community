// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.fileEditor.FileDocumentManager
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
   * Schedule to save documents, all opened projects (or only passed project if not null) and application.
   *
   * Save is not performed immediately and not finished on method call return.
   */
  fun scheduleSaveDocumentsAndProjectsAndApp(onlyProject: Project?) {
    scheduleSave(SaveTask(onlyProject))
  }

  data class SaveTask @JvmOverloads constructor(val onlyProject: Project? = null, val saveDocuments: Boolean = true, val forceSavingAllSettings: Boolean = false) {
    companion object {
      // for Java clients
      @JvmStatic
      fun projectIncludingAllSettings(project: Project) = SaveTask(onlyProject = project, saveDocuments = false, forceSavingAllSettings = true)
    }

    fun isMoreGenericThan(other: SaveTask): Boolean {
      return onlyProject == null && other.onlyProject != null && saveDocuments == other.saveDocuments && forceSavingAllSettings == other.forceSavingAllSettings
    }
  }

  @ApiStatus.Experimental
  abstract fun scheduleSave(task: SaveTask, forceExecuteImmediately: Boolean = false)

  fun scheduleProjectSave(project: Project) = scheduleSave(SaveTask(project, saveDocuments = false))

  @Deprecated("", ReplaceWith("FileDocumentManager.getInstance().saveAllDocuments()", "com.intellij.openapi.fileEditor.FileDocumentManager"))
  fun saveProjectsAndDocuments() {
    // used only by https://plugins.jetbrains.com/plugin/11072-openjml-esc
    // so, just save documents and nothing more, to simplify SaveAndSyncHandlerImpl
    FileDocumentManager.getInstance().saveAllDocuments()
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
