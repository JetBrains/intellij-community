// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeProvider.ReaderMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

@Service(Service.Level.PROJECT)
@State(name = "ReaderModeSettings", storages = [
  Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  Storage(StoragePathMacros.WORKSPACE_FILE, deprecated = true)
])
class ReaderModeSettings : PersistentStateComponentWithModificationTracker<ReaderModeSettings.State> {
  companion object {
    private val EP_READER_MODE_PROVIDER = ExtensionPointName<ReaderModeProvider>("com.intellij.readerModeProvider")
    private val EP_READER_MODE_MATCHER = ExtensionPointName<ReaderModeMatcher>("com.intellij.readerModeMatcher")

    fun getInstance(project: Project): ReaderModeSettings = project.getService(ReaderModeSettings::class.java)

    fun applyReaderMode(project: Project,
                                 editor: Editor?,
                                 file: VirtualFile?,
                                 fileIsOpenAlready: Boolean = false,
                                 forceUpdate: Boolean = false) {
      if (editor == null || file == null || PsiManager.getInstance(project).findFile(file) == null) {
        return
      }

      val matchMode = matchMode(project, file, editor)
      if (matchMode || forceUpdate) {
        for (provider in EP_READER_MODE_PROVIDER.extensionList) {
          provider.applyModeChanged(project, editor, getInstance(project).enabled && matchMode, fileIsOpenAlready)
        }
      }
    }

    @JvmStatic
    fun matchModeForStats(project: Project, file: VirtualFile): Boolean {
      val editor = (FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor)?.editor
      return getInstance(project).enabled && matchMode(project, file, editor)
    }

    fun matchMode(project: Project?, file: VirtualFile?, editor: Editor? = null): Boolean {
      if (project == null || file == null) return false
      return matchMode(project, file, editor, getInstance(project).mode)
    }

    private fun matchMode(project: Project, file: VirtualFile, editor: Editor?, mode: ReaderMode): Boolean {
      for (m in EP_READER_MODE_MATCHER.iterable) {
        val matched = m.matches(project, file, editor, mode)
        if (matched != null) return matched
      }

      if (ApplicationManager.getApplication().isHeadlessEnvironment) return false

      val inLibraries = FileIndexFacade.getInstance(project).isInLibraryClasses(file)
                        || FileIndexFacade.getInstance(project).isInLibrarySource(file)
      val isWritable = file.isWritable

      return when (mode) {
        ReaderMode.LIBRARIES_AND_READ_ONLY -> inLibraries || !isWritable
        ReaderMode.LIBRARIES -> inLibraries
        ReaderMode.READ_ONLY -> !isWritable
      }
    }
  }

  private var myState = State()

  class State : BaseState() {
    @get:ReportValue var showLigatures by property(EditorColorsManager.getInstance().globalScheme.fontPreferences.useLigatures())
    @get:ReportValue var increaseLineSpacing by property(false)
    @get:ReportValue var showRenderedDocs by property(true)
    @get:ReportValue var showInlayHints by property(true)
    @get:ReportValue var showWarnings by property(false)
    @get:ReportValue var enabled by property(Experiments.getInstance().isFeatureEnabled("editor.reader.mode"))

    var mode: ReaderMode = ReaderMode.LIBRARIES_AND_READ_ONLY
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