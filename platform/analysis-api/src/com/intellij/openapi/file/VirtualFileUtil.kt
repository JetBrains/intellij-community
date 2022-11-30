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
import com.intellij.openapi.file.NioFileUtil.toCanonicalPath
import com.intellij.openapi.fileSystem.VirtualFileSystemUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.File
import java.nio.file.Path

object VirtualFileUtil {

  fun exists(file: VirtualFile): Boolean {
    return file.exists()
  }

  fun isFile(file: VirtualFile): Boolean {
    return file.isValid && !file.isDirectory
  }

  fun isDirectory(file: VirtualFile): Boolean {
    return file.isValid && file.isDirectory
  }

  fun getTextContent(file: VirtualFile): String {
    return VfsUtil.loadText(file)
  }

  fun setTextContent(file: VirtualFile, content: String) {
    VfsUtil.saveText(file, content)
  }

  fun getBinaryContent(file: VirtualFile): ByteArray {
    return file.inputStream.use { it.readBytes() }
  }

  fun setBinaryContent(file: VirtualFile, content: ByteArray) {
    file.setBinaryContent(content)
  }

  fun findFileOrDirectory(file: VirtualFile, relativePath: String): VirtualFile? {
    return VirtualFileSystemUtil.findFileOrDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun getFileOrDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.getFileOrDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun findFile(file: VirtualFile, relativePath: String): VirtualFile? {
    return VirtualFileSystemUtil.findFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun getFile(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.getFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun findDirectory(file: VirtualFile, relativePath: String): VirtualFile? {
    return VirtualFileSystemUtil.findDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun getDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.getDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun findOrCreateFile(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun findOrCreateDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.findOrCreateDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun createFile(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.createFile(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun createDirectory(file: VirtualFile, relativePath: String): VirtualFile {
    return VirtualFileSystemUtil.createDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun deleteFileOrDirectory(file: VirtualFile, relativePath: String = ".") {
    VirtualFileSystemUtil.deleteFileOrDirectory(file.fileSystem, file.getAbsolutePath(relativePath))
  }

  fun deleteChildren(file: VirtualFile, relativePath: String = ".", predicate: (VirtualFile) -> Boolean = { true }) {
    VirtualFileSystemUtil.deleteChildren(file.fileSystem, file.getAbsolutePath(relativePath), predicate)
  }

  fun findDocument(file: VirtualFile): Document? {
    return FileDocumentManager.getInstance().getDocument(file)
  }

  fun getDocument(file: VirtualFile): Document {
    return requireNotNull(findDocument(file)) { "Cannot find Document for ${file.path}" }
  }

  fun findPsiFile(project: Project, file: VirtualFile): PsiFile? {
    return PsiManager.getInstance(project).findFile(file)
  }

  fun getPsiFile(project: Project, file: VirtualFile): PsiFile {
    return requireNotNull(findPsiFile(project, file)) { "Cannot find PSI file for ${file.path}" }
  }

  fun reloadDocument(file: VirtualFile) {
    val document = findDocument(file) ?: return
    FileDocumentManager.getInstance().reloadFromDisk(document)
  }

  fun commitDocument(project: Project, file: VirtualFile) {
    val document = findDocument(file) ?: return
    PsiDocumentManager.getInstance(project).commitDocument(document)
  }

  fun VirtualFile.toCanonicalPath(): String {
    return toNioPath().toCanonicalPath()
  }

  fun VirtualFile.toIoFile(): File {
    return toNioPath().toFile()
  }

  fun VirtualFile.getAbsolutePath(relativePath: String): String {
    return path.getAbsolutePath(relativePath)
  }

  fun VirtualFile.getAbsoluteNioPath(relativePath: String): Path {
    return path.getAbsoluteNioPath(relativePath)
  }

  fun VirtualFile.getRelativePath(file: VirtualFile): String? {
    return path.getRelativePath(file.path)
  }

  fun VirtualFile.getRelativeNioPath(file: VirtualFile): Path? {
    return path.getRelativeNioPath(file.path)
  }
}