// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.CopyReferenceUtil.getElementsToCopy
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.StringSelection

abstract class CopyPathProvider : AnAction() {
  companion object {
    @JvmField val QUALIFIED_NAME : Key<@NlsSafe String> = Key.create("QUALIFIED_NAME")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: run {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val editor = e.getData(CommonDataKeys.EDITOR)
    val customize = createCustomDataContext(e.dataContext)
    val elements = try {
      getElementsToCopy(editor, customize)
    }
    catch (e: IndexNotReadyException) {
      emptyList()
    }
    val qName = try {
      getQualifiedName(project, elements, editor, customize)
    }
    catch (e: IndexNotReadyException) {
      null
    }
    e.presentation.isEnabledAndVisible = qName != null
    e.presentation.putClientProperty(QUALIFIED_NAME, qName)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val editor = e.getData(CommonDataKeys.EDITOR)

    val customized = createCustomDataContext(e.dataContext)
    val elements = try {
      getElementsToCopy(editor, customized)
    }
    catch (e: IndexNotReadyException) {
      emptyList()
    }
    val qName = getQualifiedName(project, elements, editor, customized)
    CopyPasteManager.getInstance().setContents(StringSelection(qName))
    CopyReferenceUtil.setStatusBarText(project, IdeBundle.message("message.path.to.fqn.has.been.copied", qName))

    CopyReferenceUtil.highlight(editor, project, elements)
  }

  private fun createCustomDataContext(dataContext: DataContext): DataContext {
    val component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
    if (component !is TabLabel) return dataContext

    val file = component.info.`object`
    if (file !is VirtualFile) return dataContext

    return SimpleDataContext.builder()
      .setParent(dataContext)
      .add(LangDataKeys.VIRTUAL_FILE, file)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(file))
      .build()
  }

  @NlsSafe
  protected open fun getQualifiedName(project: Project, elements: List<PsiElement>, editor: Editor?, dataContext: DataContext): String? {
    if (elements.isEmpty()) {
      return getPathToElement(project, editor?.document?.let { FileDocumentManager.getInstance().getFile(it) }, editor)
    }

    val refs =
      elements
        .mapNotNull { getPathToElement(project, (if (it is PsiFileSystemItem) it.virtualFile else it.containingFile?.virtualFile), editor) }
        .ifEmpty { CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)?.mapNotNull { getPathToElement(project, it, editor) } }
        .orEmpty()
        .filter { it.isNotBlank() }

    return if (refs.isNotEmpty()) refs.joinToString("\n") else null
  }

  open fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): String? = null
}

abstract class DumbAwareCopyPathProvider : CopyPathProvider(), DumbAware

@ApiStatus.Internal
class CopyAbsolutePathProvider : DumbAwareCopyPathProvider() {
  override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): @NlsSafe String? = virtualFile?.presentableUrl
}

class CopyContentRootPathProvider : DumbAwareCopyPathProvider() {
  override fun getPathToElement(project: Project,
                                virtualFile: VirtualFile?,
                                editor: Editor?): String? {
    if (virtualFile == null) return null
    val root = ProjectFileIndex.getInstance(project).getContentRootForFile(virtualFile) ?: 
               WorkspaceFileIndex.getInstance(project).getContentFileSetRoot(virtualFile, false) ?: return null
    return VfsUtilCore.getRelativePath(virtualFile, root)
  }
}

@ApiStatus.Internal
class CopyFileWithLineNumberPathProvider : DumbAwareCopyPathProvider() {
  override fun getPathToElement(project: Project,
                                virtualFile: VirtualFile?,
                                editor: Editor?): String? {
    return if (virtualFile == null) null
    else editor?.let { FqnUtil.getVirtualFileFqn(virtualFile, project) + ":" + (editor.caretModel.logicalPosition.line + 1) }
  }
}

class CopySourceRootPathProvider : DumbAwareCopyPathProvider() {
  override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): @NlsSafe String? =
    virtualFile?.let {
      VfsUtilCore.getRelativePath(virtualFile, ProjectFileIndex.getInstance(project).getSourceRootForFile(virtualFile) ?: return null)
    }
}

@ApiStatus.Internal
class CopyTBXReferenceProvider : CopyPathProvider() {
  override fun getQualifiedName(project: Project,
                                elements: List<PsiElement>,
                                editor: Editor?,
                                dataContext: DataContext): String? =
    CopyTBXReferenceAction.createJetBrainsLink(project, elements, editor)
}

@ApiStatus.Internal
class CopyFileNameProvider : DumbAwareCopyPathProvider() {
  override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): String? = virtualFile?.name
}