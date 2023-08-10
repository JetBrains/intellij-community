// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.BinaryFileTypePolicy.*
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION
import org.jetbrains.annotations.ApiStatus

enum class BinaryFileTypePolicy { BINARY, NON_BINARY, BINARY_OR_NON_BINARY }

@ApiStatus.Experimental
class FileNameSuffixInputFilter(private val fileNameSuffix: String,
                                private val ignoreCase: Boolean,
                                binary: BinaryFileTypePolicy = BINARY_OR_NON_BINARY
) : BaseWeakBinaryFileInputFilter(binary, BEFORE_SUBSTITUTION) {
  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    return file.fileName.endsWith(fileNameSuffix, ignoreCase)
  }
}

@ApiStatus.Experimental
class ExactFileNameInputFilter(private val fileName: String,
                               private val ignoreCase: Boolean,
                               binary: BinaryFileTypePolicy = BINARY_OR_NON_BINARY
) : BaseWeakBinaryFileInputFilter(binary, BEFORE_SUBSTITUTION) {
  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    return file.fileName.equals(fileName, ignoreCase)
  }
}

@ApiStatus.Experimental
class FileNameExtensionInputFilter(extension: String,
                                   private val ignoreCase: Boolean,
                                   binary: BinaryFileTypePolicy = BINARY_OR_NON_BINARY
) : BaseWeakBinaryFileInputFilter(binary, BEFORE_SUBSTITUTION) {
  private val dotExtension = ".$extension"

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    return file.fileName.endsWith(dotExtension, ignoreCase)
  }
}

@ApiStatus.Experimental
abstract class BaseWeakBinaryFileInputFilter internal constructor(
  private val binary: BinaryFileTypePolicy = BINARY_OR_NON_BINARY,
  fileTypeSubstitutionStrategy: FileTypeSubstitutionStrategy
) : BaseFileTypeInputFilter(fileTypeSubstitutionStrategy) {
  // Don't do like this, this is not correct (see IDEA-303356 for example):
  //    val ext = fileNameSuffix.substringAfterLast(".")
  //    val weakFileType = if (ext != fileNameSuffix) FileTypeManager.getInstance().getFileTypeByExtension(ext) else null
  //
  // Reasons: 1. this does not work with FileTypeOverrider (which can assign any type to files with given suffix)
  //          2. this does not work with FileTypeIdentifiableByVirtualFile (same reason)
  //          3. this does not work with FileTypeDetector (same reason)
  //          4. this does not work with HashBang patterns (same reason)
  //          5. this does not work with autodetection which assigns PlainText to text files, not UnknownFileType as inferred by extension

  override fun acceptFileType(fileType: FileType): ThreeState {
    return if ((binary == BINARY && !fileType.isBinary) ||
               (binary == NON_BINARY && fileType.isBinary)) {
      ThreeState.NO
    }
    else {
      ThreeState.UNSURE // check exact filename
    }
  }
}
