// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.intellij.openapi.file

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.openapi.file.CanonicalPathUtil.getAbsoluteNioPath
import com.intellij.openapi.file.CanonicalPathUtil.getAbsolutePath
import com.intellij.openapi.file.CanonicalPathUtil.getRelativeNioPath
import com.intellij.openapi.file.CanonicalPathUtil.getRelativePath
import com.intellij.openapi.fileSystem.VirtualFileSystemUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
object VirtualFileUtil {

  @JvmStatic
  fun exists(file: VirtualFile): Boolean {
    return file.exists()
  }

  @JvmStatic
  fun isFile(file: VirtualFile): Boolean {
    return file.isValid && !file.isDirectory
  }

  @JvmStatic
  fun isDirectory(file: VirtualFile): Boolean {
    return file.isValid && file.isDirectory
  }

  @JvmStatic
  fun getTextContent(file: VirtualFile): String {
    return VfsUtil.loadText(file)
  }

  @JvmStatic
  fun setTextContent(file: VirtualFile, content: String) {
    VfsUtil.saveText(file, content)
  }

  @JvmStatic
  fun getBinaryContent(file: VirtualFile): ByteArray {
    return file.inputStream.use { it.readBytes() }
  }

  @JvmStatic
  fun setBinaryContent(file: VirtualFile, content: ByteArray) {
    file.setBinaryContent(content)
  }

  @JvmStatic
  fun findFileOrDirectory(file: VirtualFile, relativePath: String): VirtualFile? {
    return VirtualFileSystemUtil.findFileOrDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun getFileOrDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.getFileOrDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun findFile(file: VirtualFile, relativePath: String): VirtualFile? {
    return VirtualFileSystemUtil.findFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun getFile(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.getFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun findDirectory(file: VirtualFile, relativePath: String): VirtualFile? {
    return VirtualFileSystemUtil.findDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun getDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.getDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun findOrCreateFile(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun findOrCreateDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun createFile(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.createFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun createDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.createDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun deleteFileOrDirectory(file: VirtualFile, relativePath: String = ".") {
    VirtualFileSystemUtil.deleteFileOrDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  @JvmStatic
  fun deleteChildren(file: VirtualFile, relativePath: String = ".", predicate: (VirtualFile) -> Boolean = { true }) {
    VirtualFileSystemUtil.deleteChildren(file.fileSystem, file.getAbsolutePath(relativePath), predicate)
  }

  @JvmStatic
  fun findDocument(file: VirtualFile): Document? {
    return FileDocumentManager.getInstance().getDocument(file)
  }

  @JvmStatic
  fun getDocument(file: VirtualFile): Document {
    return requireNotNull(findDocument(file)) { "Cannot find Document for ${file.path}" }
  }

  @JvmStatic
  fun findPsiFile(project: Project, file: VirtualFile): PsiFile? {
    return PsiManager.getInstance(project).findFile(file)
  }

  @JvmStatic
  fun getPsiFile(project: Project, file: VirtualFile): PsiFile {
    return requireNotNull(findPsiFile(project, file)) { "Cannot find PSI file for ${file.path}" }
  }

  @JvmStatic
  fun reloadDocument(file: VirtualFile) {
    val document = findDocument(file) ?: return
    FileDocumentManager.getInstance().reloadFromDisk(document)
  }

  @JvmStatic
  fun commitDocument(project: Project, file: VirtualFile) {
    val document = findDocument(file) ?: return
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }

  @JvmStatic
  fun VirtualFile.toCanonicalPath(): String {
    return path
  }

  @JvmStatic
  fun VirtualFile.toNioPathOrNull(): Path? {
    return runCatching { toNioPath() }.getOrNull()
  }

  @JvmStatic
  fun VirtualFile.getAbsolutePath(relativePath: String): String {
    return path.getAbsolutePath(relativePath)
  }

  @JvmStatic
  fun VirtualFile.getAbsoluteNioPath(relativePath: String): Path {
    return path.getAbsoluteNioPath(relativePath)
  }

  @JvmStatic
  fun VirtualFile.getRelativePath(file: VirtualFile): String? {
    return path.getRelativePath(file.path)
  }

  @JvmStatic
  fun VirtualFile.getRelativeNioPath(file: VirtualFile): Path? {
    return path.getRelativeNioPath(file.path)
  }
}