// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeProvider.ReaderMode
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

@State(name = "ReaderModeSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ReaderModeSettings : PersistentStateComponentWithModificationTracker<ReaderModeSettings.State> {
  companion object {
    private var EP_READER_MODE_PROVIDER = ExtensionPointName<ReaderModeProvider>("com.intellij.readerModeProvider")

    @JvmStatic
    fun instance(project: Project): ReaderModeSettings {
      return ServiceManager.getService(project, ReaderModeSettings::class.java)
    }

    fun applyReaderMode(project: Project, editor: Editor?, file: VirtualFile?, fileIsOpenAlready: Boolean = false) {
      if (editor == null || file == null || PsiManager.getInstance(project).findFile(file) == null) return

      if (matchMode(project, file)) {
        EP_READER_MODE_PROVIDER.extensions().forEach {
          it.applyModeChanged(project, editor, instance(project).enabled, fileIsOpenAlready)
        }
      }
    }

    fun matchMode(project: Project?, file: VirtualFile?): Boolean {
      if (project == null || file == null) return false

      val inLibraries = FileIndexFacade.getInstance(project).isInLibraryClasses(file) || FileIndexFacade.getInstance(
        project).isInLibrarySource(file)
      val isWritable = file.isWritable

      return when (instance(project).mode) {
        ReaderMode.LIBRARIES_AND_READ_ONLY -> inLibraries || !isWritable
        ReaderMode.LIBRARIES -> inLibraries
        ReaderMode.READ_ONLY -> !isWritable
      }
    }
  }

  private var myState = State()

  class State : BaseState() {
    var showBreadcrumbs: Boolean = true
    var showLigatures: Boolean = true
    var increaseLineSpacing: Boolean = false
    var showRenderedDocs: Boolean = true
    var showInlayHints: Boolean = true
    var showWarnings: Boolean = false
    @get:ReportValue
    var enabled by property(true)
    var mode: ReaderMode = ReaderMode.LIBRARIES_AND_READ_ONLY
  }

  var showBreadcrumbs: Boolean
    get() = state.showBreadcrumbs
    set(value) {
      state.showBreadcrumbs = value
    }

  var showLigatures: Boolean
    get() = state.showLigatures
    set(value) {
      state.showLigatures = value
    }

  var increaseLineSpacing: Boolean
    get() = state.increaseLineSpacing
    set(value) {
      state.increaseLineSpacing = value
    }

  var showInlaysHints: Boolean
    get() = state.showInlayHints
    set(value) {
      state.showInlayHints = value
    }

  var showRenderedDocs: Boolean
    get() = state.showRenderedDocs
    set(value) {
      state.showRenderedDocs = value
    }

  var showWarnings: Boolean
    get() = state.showWarnings
    set(value) {
      state.showWarnings = value
    }

  var enabled: Boolean
    get() = state.enabled
    set(value) {
      state.enabled = value
    }

  var mode: ReaderMode
    get() = state.mode
    set(value) {
      state.mode = value
    }

  override fun getState(): State = myState
  override fun loadState(state: State) {
    myState = state
  }

  override fun getStateModificationCount() = state.modificationCount
}