// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.modplan.ModPlanEdit

/**
 * The languages that the ModPlan rename supports. The rename of an element of another language is not supported, so the caller takes the
 * legacy or the headless rename. This holds also for a directory or a file that holds a file of another language, and for a plan that
 * edits such a file. A file with no language, such as an image or an archive, is always supported.
 *
 * It is temporary: remove it when every language has its providers.
 */
internal object ModPlanRenameLanguages {
  private val SUPPORTED_LANGUAGE_IDS: Set<String> = setOf("JAVA", "kotlin", "Properties", "SPI", "XML", "TEXT")

  /** @return whether [language] is Java or Kotlin, the only languages whose headless processors run on the ModPlan engine */
  fun isJvmLanguage(language: Language): Boolean = language.isKindOf("JAVA") || language.isKindOf("kotlin")

  /** @return whether the ModPlan rename supports [element], see [ModPlanRenameLanguages] */
  fun isSupported(element: PsiElement): Boolean = when (element) {
    is PsiDirectory -> isSupported(element.virtualFile)
    is PsiFile -> element.viewProvider.languages.all(::isSupported) && element.virtualFile?.let(::isSupported) != false
    else -> isSupported(element.language)
  }

  /** @return the first file that [edits] change, create, move or delete, and that the ModPlan rename does not support; null if none */
  fun firstUnsupportedFile(edits: List<ModPlanEdit>): VirtualFile? {
    for (edit in edits) {
      val files: List<VirtualFile?> = when (edit) {
        is ModPlanEdit.Update -> edit.pointers.map { it.virtualFile }
        is ModPlanEdit.UpdateRange -> listOf(edit.place.virtualFile)
        is ModPlanEdit.ReplaceText -> listOf(edit.place.virtualFile)
        is ModPlanEdit.Delete -> listOf(edit.file)
        is ModPlanEdit.Move -> listOf(edit.file)
        is ModPlanEdit.Create -> {
          if (!isSupportedFileType(FileTypeRegistry.getInstance().getFileTypeByFileName(edit.name))) return edit.parent
          emptyList()
        }
        is ModPlanEdit.CreateDirectory -> emptyList()
      }
      files.firstOrNull { it != null && !isSupported(it) }?.let { return it }
    }
    return null
  }

  /** A directory is supported if every file in it, at any depth, is supported. */
  private fun isSupported(file: VirtualFile): Boolean {
    if (!file.isDirectory) return isSupportedFileType(file.fileType)
    var supported = true
    VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Unit>() {
      override fun visitFileEx(child: VirtualFile): Result {
        if (!child.isDirectory && !isSupportedFileType(child.fileType)) {
          supported = false
          return skipTo(file)
        }
        return CONTINUE
      }
    })
    return supported
  }

  private fun isSupportedFileType(fileType: FileType): Boolean =
    fileType !is LanguageFileType || isSupported(fileType.language)

  private fun isSupported(language: Language): Boolean =
    language === Language.ANY || SUPPORTED_LANGUAGE_IDS.any { language.isKindOf(it) }
}
