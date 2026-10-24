// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.quickfix.MoveFileFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.JavaBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong

internal class FileNotInSourceRootChecker : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<FileNotInSourceRootService>().init()
  }
}

private const val GROUP: Int = 1234

@ApiStatus.Internal
public const val JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES: String = "com.intellij.ide.FileNotInSourceRootChecker.no.check"

/**
 * Waits until at most [pendingChecks] checks of the open editors run.
 * The function does not wait for a check that starts after it returns.
 */
@TestOnly
@ApiStatus.Internal
public suspend fun awaitFileNotInSourceRootChecks(project: Project, pendingChecks: Int = 0) {
  project.serviceAsync<FileNotInSourceRootService>().awaitChecks(pendingChecks)
}

/** Sets the listener that runs after a background check and before its result is applied. */
@TestOnly
@ApiStatus.Internal
public suspend fun setFileNotInSourceRootCheckListener(project: Project, listener: (suspend (refresh: Boolean) -> Unit)?) {
  project.serviceAsync<FileNotInSourceRootService>().checkListener = listener
}

@Service(Service.Level.PROJECT)
private class FileNotInSourceRootService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : Disposable {
  private val checkCount = AtomicLong()
  private val latestChecks = ConcurrentCollectionFactory.createConcurrentWeakIdentityMap<FileEditor, Long>()
  /** Keeps a warning dismissed until the last editor for its file closes. */
  private val dismissedFiles = ConcurrentCollectionFactory.createConcurrentWeakIdentityMap<VirtualFile, Boolean>()
  private val activeChecks = MutableStateFlow(0)

  @Volatile
  var checkListener: (suspend (refresh: Boolean) -> Unit)? = null

  fun init() {
    if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return
    val projectConnection = project.messageBus.connect(this)
    projectConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editors = FileEditorManager.getInstance(project).getEditors(file)
        for (ed in editors) {
          checkEditor(ed)
        }
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val hasEditors = source.getAllEditors(file).any {
          it.isValid && (it !is TextEditor || !it.editor.isDisposed)
        }
        if (!hasEditors) dismissedFiles.remove(file)
      }
    })
    projectConnection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        FileEditorManager.getInstance(project).allEditors.forEach { checkEditor(it, refresh = true) }
      }
    })
    projectConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        val movedFiles = events.mapNotNull { (it as? VFileMoveEvent)?.file }
        if (movedFiles.isEmpty()) return
        for (fileEditor in FileEditorManager.getInstance(project).allEditors) {
          val file = fileEditor.file ?: continue
          if (movedFiles.any { VfsUtilCore.isAncestor(it, file, false) }) checkEditor(fileEditor, refresh = true)
        }
      }
    })
    FileEditorManager.getInstance(project).allEditors.forEach { checkEditor(it) }
  }

  @TestOnly
  suspend fun awaitChecks(pendingChecks: Int) {
    activeChecks.first { it <= pendingChecks }
  }

  private fun checkEditor(fileEditor: FileEditor, refresh: Boolean = false) {
    if (fileEditor !is TextEditor) return
    val editor = fileEditor.editor
    if (editor.project !== project) return
    if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return
    val virtualFile = editor.virtualFile ?: return
    if (!JavaFileType.INSTANCE.equals(virtualFile.fileType)) return
    if (dismissedFiles[virtualFile] == true) return
    // A newer check of the same editor makes the result of this check stale.
    val check = checkCount.incrementAndGet()
    latestChecks[fileEditor] = check

    activeChecks.update { it + 1 }
    coroutineScope.launch {
      val (path, infoAndFile) = readAction {
        virtualFile.path to highlightEditorInBackground(virtualFile, editor, refresh)
      }
      if (infoAndFile == null && !refresh) return@launch
      checkListener?.invoke(refresh)

      edtWriteAction {
        if (!fileEditor.isValid || latestChecks[fileEditor] != check || dismissedFiles[virtualFile] == true) return@edtWriteAction
        if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return@edtWriteAction
        if (virtualFile.path != path) {
          checkEditor(fileEditor, refresh = true)
          return@edtWriteAction
        }
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiJavaFile ?: return@edtWriteAction
        if (!psiFile.isValid) return@edtWriteAction
        if (infoAndFile != null && infoAndFile.second !== psiFile) {
          checkEditor(fileEditor, refresh = true)
          return@edtWriteAction
        }
        val analyzer = DaemonCodeAnalyzerEx.getInstanceEx(project)
        if (refresh) analyzer.cleanFileLevelHighlights(GROUP, psiFile)
        if (infoAndFile != null) {
          analyzer.addFileLevelHighlight(GROUP, infoAndFile.first, psiFile, null, null)
        }
      }
    }.invokeOnCompletion { activeChecks.update { it - 1 } }
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  private fun highlightEditorInBackground(virtualFile: VirtualFile, editor: Editor, refresh: Boolean): Pair<HighlightInfo, PsiFile>? {
    if (project.isDisposed || editor.isDisposed) return null
    if (!JavaFileType.INSTANCE.equals(virtualFile.fileType)) return null

    val fileIndex = ProjectFileIndex.getInstance(project)
    if (fileIndex.isInSource(virtualFile) || fileIndex.isExcluded(virtualFile) || fileIndex.isUnderIgnored(virtualFile)) return null
    if (fileIndex.findContainingLibraries(virtualFile).isNotEmpty() || fileIndex.findContainingSdks(virtualFile).isNotEmpty()) return null

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiJavaFile ?: return null
    if (!refresh && DaemonCodeAnalyzerEx.getInstanceEx(project).hasFileLevelHighlights(GROUP, psiFile)) return null
    val packageName = psiFile.packageName
    val module = fileIndex.getModuleForFile(virtualFile) ?: return null
    val rootModel = DefaultModulesProvider.createForProject(project).getRootModel(module)
    val roots = rootModel.sourceRoots
    if (roots.isEmpty()) return null

    var root = roots[0]
    if (packageName.isNotEmpty()) {
      root = VfsUtil.findRelativeFile(root, *packageName.split('.').toTypedArray()) ?: root
    }
    if (root.findChild(virtualFile.name) != null) return null

    val moveFileFix = MoveFileToSourceRootFix(virtualFile, root)
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
      .range(psiFile)
      .description(JavaBundle.message("warning.java.file.outside.source.root"))
      .fileLevelAnnotation()
      .group(GROUP)
      .registerFix(moveFileFix, listOf(DismissFix(), IgnoreForThisProjectFix()), null, null, null)
      .create()

    return info?.let { Pair(info, psiFile) }
  }

  override fun dispose() {
  }

  // Separate fix to differentiate in statistics
  class MoveFileToSourceRootFix(virtualFile: VirtualFile, root: VirtualFile) :
    MoveFileFix(virtualFile, root, JavaBundle.message("fix.move.to.source.root"))

  inner class DismissFix : IntentionAction {
    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = familyName
    override fun getFamilyName(): String = JavaBundle.message("intention.family.name.dismiss")
    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
      psiFile ?: return
      dismissedFiles[psiFile.virtualFile] = true
      DaemonCodeAnalyzerEx.getInstanceEx(project).cleanFileLevelHighlights(GROUP, psiFile)
    }
  }

  class IgnoreForThisProjectFix : IntentionAction {
    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = familyName
    override fun getFamilyName(): String = JavaBundle.message("intention.family.name.ignore.project")
    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      file ?: return
      val codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project)
      PropertiesComponent.getInstance(project).setValue(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, true)
      codeAnalyzer.cleanFileLevelHighlights(GROUP, file)
      for (ed in EditorFactory.getInstance().allEditors) {
        if (ed.project != project) continue
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(ed.document) as? PsiJavaFile ?: continue
        if (psiFile == file) continue
        codeAnalyzer.cleanFileLevelHighlights(GROUP, psiFile)
      }
    }
  }
}
