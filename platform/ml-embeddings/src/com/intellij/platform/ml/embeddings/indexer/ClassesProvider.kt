// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.toArray
import com.intellij.util.indexing.FileContent
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ClassesProvider {
  fun extract(fileContent: FileContent): List<IndexableClass>

  companion object {
    private val EXTENSION = FileTypeExtension<ClassesProvider>("com.intellij.embeddings.indexer.classesProvider")

    val supportedFileTypes: Array<FileType>
      get() = EXTENSION.getAllRegisteredExtensions().keys.toArray(FileType.EMPTY_ARRAY)

    @RequiresReadLock
    fun extractClasses(fileContent: FileContent): List<IndexableClass> {
      ThreadingAssertions.assertReadAccess() // annotation doesn't work in Kotlin
      return EXTENSION.forFileType(fileContent.fileType)?.extract(fileContent) ?: emptyList()
    }
  }
}