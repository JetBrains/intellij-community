// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems.pass

import com.intellij.codeInsight.daemon.problems.FileStateCache.Companion.getInstance
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.removeState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.setPreviousState
import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemCodeVisionProvider.Companion.hintsEnabled
import com.intellij.codeInsight.hints.InlayHintsPassFactory.Companion.restartDaemonUpdatingHints
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.InlayHintsSettings.Companion.INLAY_SETTINGS_CHANGED
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.testFramework.TestModeFlags

/**
 * Listener that reacts to user initiated changes and updates current problems state.
 *
 *
 * Events that are handled by this listener:<br></br>
 * 1. selection change (user opened different file) -> store current timestamp for closed file and restores state for new file<br></br>
 * 2. VFS changes -> remove states for deleted files, remove problems for updated files (in order to recalculate them later)<br></br>
 * 3. hints settings change -> rollback file state, so that there are no reported problems yet (but they can be found using a rolled-back state)<br></br>
 * 4. refactoring done for member -> rollback member file state<br></br>
 * 5. PSI tree changed -> rollback file state for all the editors with this file<br></br>
 */
internal class ProjectProblemFileSelectionListener(private val project: Project)
  : PsiTreeChangeAdapter(), FileEditorManagerListener, InlayHintsSettings.SettingsListener, BulkFileListener, RefactoringEventListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    if (!hintsEnabled(project)) {
      return
    }

    val oldJavaFile = getJavaFile(project, event.oldFile)
    if (oldJavaFile != null) {
      val oldEditor = getEditor(event.oldEditor)
      if (oldEditor != null) {
        ProjectProblemUtils.updateTimestamp(oldJavaFile, oldEditor)
      }
    }

    val newJavaFile = getJavaFile(project, event.newFile)
    if (newJavaFile == null || oldJavaFile === newJavaFile) {
      return
    }

    val newEditor = getEditor(event.newEditor)
    if (newEditor == null || !ProjectProblemUtils.isProjectUpdated(newJavaFile, newEditor)) {
      return
    }

    setPreviousState(newJavaFile)
    val isInSplitEditorMode = FileEditorManager.getInstance(project).selectedEditors.size > 1
    if (isInSplitEditorMode) {
      restartDaemonUpdatingHints(project)
    }
  }

  override fun before(events: List<VFileEvent>) {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    for (e in events) {
      if (e is VFileDeleteEvent) {
        val changedFile = e.file
        if (!fileIndex.isInContent(changedFile)) {
          continue
        }
        val removedJavaFile = getJavaFile(project, changedFile) ?: continue
        removeState(removedJavaFile)
      }

      if (e is VFileContentChangeEvent || e is VFileDeleteEvent) {
        val selectedFile = selectedFile
        if (selectedFile == null || e.file == selectedFile) {
          continue
        }
        val selectedJavaFile = getJavaFile(project, selectedFile) ?: continue
        setPreviousState(selectedJavaFile)
      }
    }
  }

  private val selectedFile: VirtualFile?
    get() = (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.file

  override fun settingsChanged() {
    if (!hintsEnabled(project)) {
      onHintsDisabled()
    }
  }

  override fun languageStatusChanged() {
    if (!hintsEnabled(project)) {
      onHintsDisabled()
    }
  }

  override fun globalEnabledStatusChanged(newEnabled: Boolean) {
    if (!hintsEnabled(project)) {
      onHintsDisabled()
    }
  }

  private fun onHintsDisabled() {
    val editorManager = FileEditorManager.getInstance(project)
    for (selectedEditor in editorManager.selectedEditors) {
      val virtualFile = selectedEditor.file ?: continue
      val psiJavaFile = getJavaFile(project, virtualFile) ?: continue
      setPreviousState(psiJavaFile)
    }
  }

  override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {}

  override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
    val psiJavaFile = (afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) as? PsiMember)?.containingFile as? PsiJavaFile
                      ?: return
    setPreviousState(psiJavaFile)
  }

  override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {}

  override fun undoRefactoring(refactoringId: String) {
    val selectedFile = selectedFile ?: return
    val psiJavaFile = getJavaFile(project, selectedFile) ?: return
    setPreviousState(psiJavaFile)
  }

  override fun beforeChildAddition(event: PsiTreeChangeEvent) {
    updateFileState(event.file)
  }

  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
    updateFileState(event.file)
  }

  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
    updateFileState(event.file)
  }

  override fun beforeChildMovement(event: PsiTreeChangeEvent) {
    updateFileState(event.file)
  }

  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    updateFileState(event.file)
  }

  override fun beforePropertyChange(event: PsiTreeChangeEvent) {
    updateFileState(event.file)
  }

  private fun updateFileState(psiFile: PsiFile?) {
    val changedFile = PsiUtilCore.getVirtualFile(psiFile) ?: return
    val editors = EditorFactory.getInstance().allEditors
    val fileIndex = ProjectFileIndex.getInstance(project)
    for (editor in editors) {
      val editorImpl = editor as? EditorImpl ?: continue
      if (!editorImpl.contentComponent.isShowing && !ApplicationManager.getApplication().isUnitTestMode) {
        continue
      }

      var editorFile = editorImpl.virtualFile
      if (editorFile == null) {
        val document = editorImpl.document
        editorFile = FileDocumentManager.getInstance().getFile(document)
      }

      if (editorFile == null || changedFile == editorFile || !fileIndex.isInContent(editorFile)) {
        continue
      }
      val psiJavaFile = getJavaFile(project, editorFile) ?: continue
      setPreviousState(psiJavaFile)
    }
  }

  internal class MyStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment && !TestModeFlags.`is`(ProjectProblemUtils.ourTestingProjectProblems)) {
        return
      }

      val listener = ProjectProblemFileSelectionListener(project)
      val connection = project.messageBus.connect()
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener)
      connection.subscribe(INLAY_SETTINGS_CHANGED, listener)
      connection.subscribe(VirtualFileManager.VFS_CHANGES, listener)
      connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, listener)
      PsiManager.getInstance(project).addPsiTreeChangeListener(listener, getInstance(project))
    }
  }
}

private fun getJavaFile(project: Project, file: VirtualFile?): PsiJavaFile? {
  if (file == null || file is VirtualFileWindow || !file.isValid) {
    return null
  }

  return if (ProjectProblemUtils.containsJvmLanguage(file)) PsiManager.getInstance(project).findFile(file) as? PsiJavaFile else null
}

private fun getEditor(fileEditor: FileEditor?): Editor? = (fileEditor as? TextEditor)?.editor
