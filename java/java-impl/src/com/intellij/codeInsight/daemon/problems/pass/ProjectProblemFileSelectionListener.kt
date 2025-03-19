// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems.pass

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.daemon.problems.FileStateCache
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.removeState
import com.intellij.codeInsight.daemon.problems.FileStateUpdater.Companion.setPreviousState
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.jvm.JvmLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.testFramework.TestModeFlags
import com.intellij.util.SlowOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

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
      InlayHintsPassFactoryInternal.Companion.restartDaemonUpdatingHints(project, "ProjectProblemFileFileEditorManagerListener.selectionChanged")
    }
  }
}

internal class ProjectProblemFileInlaySelectionListenerSettingsListener(private val project: Project) : InlayHintsSettings.SettingsListener {
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

internal class ProjectProblemFileRefactoringEventListener(private val project: Project) : RefactoringEventListener {
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

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
private class ProjectPsiChangesProcessor(private val scope: CoroutineScope) {
  private val psiChanges = ConcurrentCollectionFactory.createConcurrentSet<PsiFile>()
  private val psiChangesProcessor = MutableSharedFlow<Unit?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch {
      psiChangesProcessor.debounce(2.seconds).collect {
        for (file in psiChanges.toList()) {
          readAction {
            if (file.isValid) {
              updateFileState(file, file.project)
            }
          }
          psiChanges.remove(file)
        }
      }
    }
  }

  fun submitPsiChange(event: PsiTreeChangeEvent) {
    val file = event.file ?: return
    if (file.language is JvmLanguage) {
      psiChanges.add(file)
      assert(psiChangesProcessor.tryEmit(null))
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
}

private class ProjectProblemFileSelectionListenerStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment && !TestModeFlags.`is`(ProjectProblemUtils.ourTestingProjectProblems)) {
      return
    }

    val parentDisposable = project.serviceAsync<FileStateCache>()

    VirtualFileManager.getInstance().addAsyncFileListener({ events ->
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      val removedFiles = mutableListOf<PsiJavaFile>()
      val contentChangedFiles = mutableListOf<PsiJavaFile>()
      val selectedFile = getSelectedFile(project)

      for (event in events) {
        if (event is VFileDeleteEvent) {
          val deletedFile = event.file
          if (fileIndex.isInContent(deletedFile)) {
            getJavaFile(project, deletedFile)?.let { removedFiles.add(it) }
          }
        }

        if (event is VFileContentChangeEvent || event is VFileDeleteEvent) {
          if (selectedFile != null && event.file != selectedFile) {
            getJavaFile(project, selectedFile)?.let { contentChangedFiles.add(it) }
          }
        }
      }

      object : AsyncFileListener.ChangeApplier {
        override fun beforeVfsChange() {
          for (removedFile in removedFiles) {
            removeState(removedFile)
          }

          for (javaFile in contentChangedFiles) {
            setPreviousState(javaFile)
          }
        }
      }
    }, parentDisposable)

    val psiChangesProcessor = project.serviceAsync<ProjectPsiChangesProcessor>()
    project.serviceAsync<PsiManager>().addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      private fun addToQueue(event: PsiTreeChangeEvent) {
        psiChangesProcessor.submitPsiChange(event)
      }

      override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        addToQueue(event)
      }

      override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        addToQueue(event)
      }

      override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        addToQueue(event)
      }

      override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        addToQueue(event)
      }

      override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        addToQueue(event)
      }

      override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        addToQueue(event)
      }
    }, parentDisposable)
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

private fun getSelectedFile(project: Project): VirtualFile? {
  return (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.file
}

private fun getJavaFile(project: Project, file: VirtualFile?): PsiJavaFile? {
  if (file == null || file is VirtualFileWindow || !file.isValid ||
    !ProjectProblemUtils.containsJvmLanguage(file)) {
    return null
  }
  SlowOperations.knownIssue("IDEA-334994, EA-852866").use {
    return PsiManager.getInstance(project).findFile(file) as? PsiJavaFile
  }
}

private fun getEditor(fileEditor: FileEditor?): Editor? = (fileEditor as? TextEditor)?.editor