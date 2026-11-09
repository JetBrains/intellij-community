// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.smartPointers.SelfElementInfo
import org.jetbrains.annotations.ApiStatus

/**
 * The anchor of a [PsiFileNode].
 *
 * The anchor holds the project, the virtual file, the root language id and the PSI class name of the file.
 * It holds no PSI and no code insight context.
 * [getElement] restores the file with [SelfElementInfo.restoreFileFromVirtual], the restore path of a file smart pointer,
 * in any code insight context.
 * [getElement] returns null when the file type or the PSI class of the file changed, for example after a rename.
 * A subclass of [PsiFileNode] can cast the value to its PSI class.
 * So the anchor never returns a file of another class.
 * [getElement] takes a read action, so a direct caller needs none. The anchor caches no PSI.
 * [getRange] loads the document of the file, as the range of a file smart pointer does.
 * [SmartPointerManager][com.intellij.psi.SmartPointerManager] does not know the anchor: it creates no tracker,
 * and `removePointer` ignores a pointer that is not its own.
 *
 * Two anchors are equal when their virtual files are equal. [toString] prints `file{<file>}`.
 *
 * [PsiFileNode.canRepresent] checks only that the virtual file is valid. It does no PSI lookup.
 */
@ApiStatus.Internal
class PsiFileSystemItemAnchor private constructor(
  private val project: Project,
  private val virtualFile: VirtualFile,
  private val languageId: String,
  private val fileClassName: String,
) : SmartPsiElementPointer<PsiFile> {
  override fun getProject(): Project = project

  override fun getVirtualFile(): VirtualFile = virtualFile

  override fun getElement(): PsiFile? = runReadActionBlocking {
    if (project.isDisposed) {
      return@runReadActionBlocking null
    }
    val language = Language.findLanguageByID(languageId) ?: return@runReadActionBlocking null
    SelfElementInfo.restoreFileFromVirtual(virtualFile, project, language, context = null)
      ?.takeIf { it.javaClass.name == fileClassName }
  }

  override fun getContainingFile(): PsiFile? = getElement()

  override fun getRange(): Segment? = SelfElementInfo.fileRange(virtualFile)

  override fun getPsiRange(): Segment? = SelfElementInfo.filePsiRange(project, virtualFile)

  override fun equals(other: Any?): Boolean = other is PsiFileSystemItemAnchor && virtualFile == other.virtualFile

  override fun hashCode(): Int = virtualFile.hashCode()

  override fun toString(): String = "file{$virtualFile}"

  companion object {
    /**
     * Creates the anchor of [file]. The caller checks that [file] is valid and has a virtual file.
     * The caller needs no read action.
     * A write action can change the language or the PSI class after the call, so the anchor can store stale values.
     * Then [getElement] returns null.
     */
    @JvmStatic
    fun of(file: PsiFile): PsiFileSystemItemAnchor =
      PsiFileSystemItemAnchor(file.project, file.virtualFile, LanguageUtil.getRootLanguage(file).id, file.javaClass.name)
  }
}
