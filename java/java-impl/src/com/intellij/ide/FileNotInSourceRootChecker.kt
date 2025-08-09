// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.quickfix.MoveFileFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.JavaBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileMoveEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class FileNotInSourceRootChecker : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<FileNotInSourceRootService>().init()
  }
}

private const val GROUP: Int = 1234
private const val JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES: String = "com.intellij.ide.FileNotInSourceRootChecker.no.check"

@Service(Service.Level.PROJECT)
private class FileNotInSourceRootService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : Disposable {
  fun init() {
    if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return
    val messageBus = ApplicationManager.getApplication().messageBus
    messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editors = FileEditorManager.getInstance(project).getEditors(file)
        for (ed in editors) {
          checkEditor(ed)
        }
      }
    })
    FileEditorManager.getInstance(project).allEditors.forEach { checkEditor(it) }
  }

  private fun checkEditor(fileEditor: FileEditor) {
    if (fileEditor !is TextEditor) return
    val editor = fileEditor.editor
    if (editor.project !== project) return
    if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return
    val virtualFile = editor.virtualFile ?: return

    coroutineScope.launch {
      val infoAndFile = readAction {
        highlightEditorInBackground(virtualFile, editor)
      }

      if (infoAndFile != null) {
        edtWriteAction {
          if (!fileEditor.isValid) return@edtWriteAction
          val psiFile = infoAndFile.second
          if (!psiFile.isValid) return@edtWriteAction
          val listener = object : VirtualFileListener {
            override fun fileMoved(event: VirtualFileMoveEvent) {
              val file = event.file
              if (file == psiFile.virtualFile) {
                file.fileSystem.removeVirtualFileListener(this)
                if (editor.isDisposed) return
                DaemonCodeAnalyzerEx.getInstanceEx(project).cleanFileLevelHighlights(GROUP, psiFile)
                checkEditor(fileEditor)
              }
            }
          }
          val fileSystem = psiFile.virtualFile.fileSystem
          fileSystem.addVirtualFileListener(listener)
          Disposer.register(fileEditor) { fileSystem.removeVirtualFileListener(listener) }

          DaemonCodeAnalyzerEx.getInstanceEx(project).addFileLevelHighlight(
            GROUP, infoAndFile.first, infoAndFile.second, null, null
          )
        }
      }
    }
  }

  @RequiresReadLock
  private fun highlightEditorInBackground(virtualFile: VirtualFile, editor: Editor): Pair<HighlightInfo, PsiFile>? {
    if (project.isDisposed || editor.isDisposed) return null
    if (!JavaFileType.INSTANCE.equals(virtualFile.fileType)) return null

    val fileIndex = ProjectFileIndex.getInstance(project)
    if (fileIndex.isInSource(virtualFile) || fileIndex.isExcluded(virtualFile) || fileIndex.isUnderIgnored(virtualFile)) return null
    if (fileIndex.getOrderEntriesForFile(virtualFile).isNotEmpty()) return null

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiJavaFile ?: return null
    if (DaemonCodeAnalyzerEx.getInstanceEx(project).hasFileLevelHighlights(GROUP, psiFile)) return null
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

  class DismissFix : IntentionAction {
    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = familyName
    override fun getFamilyName(): String = JavaBundle.message("intention.family.name.dismiss")
    override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
      psiFile ?: return
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
