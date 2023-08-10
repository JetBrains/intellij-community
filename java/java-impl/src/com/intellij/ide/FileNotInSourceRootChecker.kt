// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import java.util.concurrent.ForkJoinPool

class FileNotInSourceRootChecker : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.getService(FileNotInSourceRootService::class.java).init()
  }
}

private const val GROUP : Int = 1234
private const val JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES : String = "com.intellij.ide.FileNotInSourceRootChecker.no.check"

@Service(Service.Level.PROJECT)
class FileNotInSourceRootService(val project: Project) : Disposable {
  
  fun init() {
    if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return
    val editorFactory = EditorFactory.getInstance()
    editorFactory.addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        checkEditor(event.editor)
      }
    }, this)
    editorFactory.allEditors.forEach { editor -> checkEditor(editor) }
  }

  private fun checkEditor(editor: Editor) {
    if (editor.project !== project) return
    if (PropertiesComponent.getInstance(project).getBoolean(JAVA_DONT_CHECK_OUT_OF_SOURCE_FILES, false)) return
    val virtualFile = editor.virtualFile ?: return
    ReadAction.nonBlocking { 
      highlightEditorInBackground(virtualFile, editor)
    }.submit(ForkJoinPool.commonPool())
  }

  private fun highlightEditorInBackground(virtualFile: VirtualFile, editor: Editor) {
    if (project.isDisposed) return
    if (!JavaFileType.INSTANCE.equals(virtualFile.fileType)) return
    val fileIndex = ProjectFileIndex.getInstance(project)
    if (fileIndex.isInSource(virtualFile) || fileIndex.isExcluded(virtualFile) || fileIndex.isUnderIgnored(virtualFile)) return
    if (fileIndex.getOrderEntriesForFile(virtualFile).isNotEmpty()) return
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? PsiJavaFile ?: return
    val packageName = psiFile.packageName
    val module = fileIndex.getModuleForFile(virtualFile) ?: return
    val rootModel = DefaultModulesProvider.createForProject(project).getRootModel(module)
    val roots = rootModel.sourceRoots
    if (roots.isEmpty()) return
    var root = roots[0]
    if (packageName.isNotEmpty()) {
      root = VfsUtil.findRelativeFile(root, *packageName.split('.').toTypedArray()) ?: root
    }
    if (root.findChild(virtualFile.name) != null) return
    val moveFileFix = MoveFileToSourceRootFix(virtualFile, root)
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
      .range(psiFile)
      .description(JavaBundle.message("warning.java.file.outside.source.root"))
      .fileLevelAnnotation()
      .group(GROUP)
      .registerFix(moveFileFix, listOf(DismissFix(), IgnoreForThisProjectFix()), null, null, null)
      .create()
    if (info != null) {
      ApplicationManager.getApplication().invokeLater({
        DaemonCodeAnalyzerEx.getInstanceEx(project).addFileLevelHighlight(GROUP, info, psiFile)
      }, project.disposed)
    }
  }

  override fun dispose() {
  }

  // Separate fix to differentiate in statistics  
  class MoveFileToSourceRootFix(virtualFile: VirtualFile, root: VirtualFile): 
    MoveFileFix(virtualFile, root, JavaBundle.message("fix.move.to.source.root"))

  class DismissFix: IntentionAction {
    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = familyName
    override fun getFamilyName(): String = JavaBundle.message("intention.family.name.dismiss")
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      file ?: return
      DaemonCodeAnalyzerEx.getInstanceEx(project).cleanFileLevelHighlights(GROUP, file)
    }
  }

  class IgnoreForThisProjectFix: IntentionAction {
    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = familyName
    override fun getFamilyName(): String = JavaBundle.message("intention.family.name.ignore.project")
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

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
