// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ClassesProvider {
  fun extract(file: PsiFile): List<IndexableClass>

  companion object {
    private val EXTENSION = FileTypeExtension<ClassesProvider>("com.intellij.searcheverywhere.ml.classesProvider")

    @RequiresReadLock
    fun extractClasses(file: PsiFile): List<IndexableClass> {
      ThreadingAssertions.assertReadAccess() // annotation doesn't work in Kotlin
      return EXTENSION.forFileType(file.fileType).extract(file)
    }
  }
}