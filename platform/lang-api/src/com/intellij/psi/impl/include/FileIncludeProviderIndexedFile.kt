// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.include

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexedFile

/**
 * A marker subtype of [IndexedFile] used to break recursion in
 * the [FileIncludeProvider.acceptFile] delegation chain.
 * 
 * This helps to maintain backward compatibility for clients not yet migrated
 * to `FileIncludeProvider.acceptFile(IndexedFile)`.
 */
internal class FileIncludeProviderIndexedFile(private val file: VirtualFile) : UserDataHolderBase(), IndexedFile {
  override fun getFileType(): FileType = file.fileType

  override fun getFile(): VirtualFile = file

  override fun getFileName(): String = file.name

  override fun getProject(): Project? = null
}
