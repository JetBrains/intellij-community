// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.codeInsight.navigation.openFileWithPsiElementAsync
import com.intellij.ide.IdeBundle
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.NavigatableWithText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class PsiFileNode(project: Project?, value: PsiFile, viewSettings: ViewSettings?)
  : BasePsiNode<PsiFile>(project, value, viewSettings), NavigatableWithText {
  public override fun getChildrenImpl(): Collection<AbstractTreeNode<*>>? {
    val project = project
    val jarRoot = jarRoot
    if (project != null && jarRoot != null) {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(jarRoot)
      if (psiDirectory != null) {
        return ProjectViewDirectoryHelper.getInstance(project).getDirectoryChildren(psiDirectory, settings, true)
      }
    }

    return emptyList()
  }

  private val isArchive: Boolean
    get() {
      val file = virtualFile
      return file != null && file.isValid && FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE)
    }

  override fun updateImpl(data: PresentationData) {
    val value = value ?: return
    data.presentableText = value.name
    data.setIcon(value.getIcon(Iconable.ICON_FLAG_READ_STATUS))

    val file = virtualFile
    if (file != null && file.`is`(VFileProperty.SYMLINK)) {
      val target: @NlsSafe String? = file.canonicalPath
      if (target == null) {
        data.setAttributesKey(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
        data.tooltip = IdeBundle.message("node.project.view.bad.link")
      }
      else {
        data.tooltip = FileUtil.toSystemDependentName(target)
      }
    }
  }

  override fun canNavigate(): Boolean = isNavigatableLibraryRoot || super<BasePsiNode>.canNavigate()

  private val isNavigatableLibraryRoot: Boolean
    get() {
      val jarRoot = jarRoot
      val project = project
      if (jarRoot != null && project != null && ProjectRootsUtil.isLibraryRoot(jarRoot, project)) {
        val orderEntry = LibraryUtil.findLibraryEntry(jarRoot, project)
        return orderEntry != null && ProjectSettingsService.getInstance(project).canOpenLibraryOrSdkSettings(orderEntry)
      }
      return false
    }

  private val jarRoot: VirtualFile?
    get() {
      val file = virtualFile
      if (file == null || !file.isValid || file.fileType !is ArchiveFileType) {
        return null
      }
      return JarFileSystem.getInstance().getJarRootForLocalFile(file)
    }

  final override fun navigate(requestFocus: Boolean) {
    val jarRoot = jarRoot
    val project = project
    if (requestFocus && jarRoot != null && project != null && ProjectRootsUtil.isLibraryRoot(jarRoot, project)) {
      val orderEntry = LibraryUtil.findLibraryEntry(jarRoot, project)
      if (orderEntry != null) {
        ProjectSettingsService.getInstance(project).openLibraryOrSdkSettings(orderEntry)
        return
      }
    }

    super<BasePsiNode>.navigate(requestFocus)
  }

  internal suspend fun navigateAsync(requestFocus: Boolean) {
    val jarRoot = jarRoot
    val project = project
    if (requestFocus && jarRoot != null && project != null) {
      val orderEntry = readAction {
        if (ProjectRootsUtil.isLibraryRoot(jarRoot, project)) {
          LibraryUtil.findLibraryEntry(jarRoot, project)
        }
        else {
          null
        }
      }
      if (orderEntry != null) {
        val projectSettingsService = project.serviceAsync<ProjectSettingsService>()
        withContext(Dispatchers.EDT) {
          blockingContext {
            projectSettingsService.openLibraryOrSdkSettings(orderEntry)
          }
        }
        return
      }
    }

    if (this::class.java === PsiFileNode::class.java) {
      if (readAction { canNavigate() }) {
        if (requestFocus) {
          openFileWithPsiElementAsync(element = extractPsiFromValue()!!, searchForOpen = true, requestFocus = true)
        }
        else {
          navigationItem?.navigate(/* requestFocus = */ false)
        }
      }
    }
    else {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          navigate(requestFocus, false)
        }
      }
    }
  }

  override fun getNavigateActionText(focusEditor: Boolean): String? {
    return if (isNavigatableLibraryRoot) ActionsBundle.message("action.LibrarySettings.navigate") else null
  }

  override fun getWeight(): Int = 20

  override fun getTitle(): String? {
    val file = virtualFile
    return if (file == null) super.getTitle() else FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
  }

  final override fun isMarkReadOnly(): Boolean = true

  override fun getTypeSortKey(): Comparable<ExtensionSortKey?>? {
    return ExtensionSortKey(extension(value) ?: return null)
  }

  class ExtensionSortKey(private val ext: String) : Comparable<ExtensionSortKey?> {
    override fun compareTo(other: ExtensionSortKey?): Int = if (other == null) 0 else ext.compareTo(other.ext)
  }

  override fun shouldDrillDownOnEmptyElement(): Boolean {
    val file = value
    @Suppress("DEPRECATION")
    return file != null && file.fileType === com.intellij.openapi.fileTypes.StdFileTypes.JAVA
  }

  override fun canRepresent(element: Any?): Boolean {
    if (super.canRepresent(element)) {
      return true
    }

    val value = value
    return value != null && element != null && element == value.virtualFile
  }

  override fun contains(file: VirtualFile): Boolean {
    return super.contains(file) || isArchive && Comparing.equal(VfsUtil.getLocalFile(file), virtualFile)
  }
}

private fun extension(file: PsiFile?): String? {
  val vFile = file?.virtualFile ?: return null
  val type = vFile.fileType
  val defaultExtension = type.defaultExtension
  when {
    !defaultExtension.isEmpty() -> {
      // If the type defines a default extension, use it to group files of the same type together,
      // regardless of their actual extension (e.g. *.htm, *.html...).
      return defaultExtension
    }
    type !== FileTypes.UNKNOWN -> {
      // Otherwise, fall back to the type name, again, to group files of the same type together.
      return type.name
    }
    else -> {
      // If the type is unknown, fall back to the actual extension, convert it to a lower case, again, to group things together.
      val extension = vFile.extension
      return if (extension.isNullOrEmpty()) defaultExtension else extension.lowercase()
    }
  }
}
