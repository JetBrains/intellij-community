// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems.pass

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactory.Companion.restartDaemonUpdatingHints
import com.intellij.codeInsight.daemon.problems.FileStateCache
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.removeState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.setPreviousState
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
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
 * Events that are handled by this listener:<br></br>
 * 1. selection change (user opened different file) -> store current timestamp for closed file and restores state for new file<br></br>
 * 2. VFS changes -> remove states for deleted files, remove problems for updated files (in order to recalculate them later)<br></br>
 * 3. hints settings change -> rollback file state, so that there are no reported problems yet (but they can be found using a rolled-back state)<br></br>
 * 4. refactoring done for member -> rollback member file state<br></br>
 * 5. PSI tree changed -> rollback file state for all the editors with this file<br></br>
 */

private class ProjectProblemFileFileEditorManagerListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project
    if (!isCodeVisionEnabled(project)) {
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
    val isInSplitEditorMode = event.manager.selectedEditors.size > 1
    if (isInSplitEditorMode) {
      restartDaemonUpdatingHints(project)
    }
  }
}

private class ProjectProblemFileInlaySelectionListenerSettingsListener(private val project: Project) : InlayHintsSettings.SettingsListener {
  override fun settingsChanged() {
    if (!isCodeVisionEnabled(project)) {
      onHintsDisabled(project)
    }
  }

  override fun languageStatusChanged() {
    if (!isCodeVisionEnabled(project)) {
      onHintsDisabled(project)
    }
  }

  override fun globalEnabledStatusChanged(newEnabled: Boolean) {
    if (!isCodeVisionEnabled(project)) {
      onHintsDisabled(project)
    }
  }
}

private class ProjectProblemFileRefactoringEventListener(private val project: Project) : RefactoringEventListener {
  override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
    val psiJavaFile = (afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) as? PsiMember)?.containingFile as? PsiJavaFile
                      ?: return
    setPreviousState(psiJavaFile)
  }

  override fun undoRefactoring(refactoringId: String) {
    val selectedFile = getSelectedFile(project) ?: return
    val psiJavaFile = getJavaFile(project, selectedFile) ?: return
    setPreviousState(psiJavaFile)
  }
}

private class ProjectProblemFileSelectionListenerStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment && !TestModeFlags.`is`(ProjectProblemUtils.ourTestingProjectProblems)) {
      return
    }

    val connection = project.messageBus.simpleConnect()
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
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
            val selectedFile = getSelectedFile(project)
            if (selectedFile == null || e.file == selectedFile) {
              continue
            }
            val selectedJavaFile = getJavaFile(project, selectedFile) ?: continue
            setPreviousState(selectedJavaFile)
          }
        }
      }
    })
    PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        updateFileState(event.file, project)
      }

      override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        updateFileState(event.file, project)
      }

      override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        updateFileState(event.file, project)
      }

      override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        updateFileState(event.file, project)
      }

      override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        updateFileState(event.file, project)
      }

      override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        updateFileState(event.file, project)
      }
    }, FileStateCache.getInstance(project))
  }
}

private fun onHintsDisabled(project: Project) {
  val editorManager = FileEditorManager.getInstance(project)
  for (selectedEditor in editorManager.selectedEditors) {
    val virtualFile = selectedEditor.file ?: continue
    val psiJavaFile = getJavaFile(project, virtualFile) ?: continue
    setPreviousState(psiJavaFile)
  }
}

private fun updateFileState(psiFile: PsiFile?, project: Project) {
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

private fun getSelectedFile(project: Project): VirtualFile? {
  return (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.file
}

private fun getJavaFile(project: Project, file: VirtualFile?): PsiJavaFile? {
  if (file == null || file is VirtualFileWindow || !file.isValid) {
    return null
  }

  return if (ProjectProblemUtils.containsJvmLanguage(file)) PsiManager.getInstance(project).findFile(file) as? PsiJavaFile else null
}

private fun getEditor(fileEditor: FileEditor?): Editor? = (fileEditor as? TextEditor)?.editor