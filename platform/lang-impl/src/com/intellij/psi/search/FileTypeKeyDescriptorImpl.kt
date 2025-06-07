// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl

internal class FileTypeKeyDescriptorImpl : FileTypeKeyDescriptor() {
  private val lazyEnumerator by lazy {
    val indexImpl = FileBasedIndex.getInstance() as FileBasedIndexImpl
    indexImpl.getIndex(FileTypeIndex.NAME) as FileTypeNameEnumerator
  }

  override fun getEnumerator(): FileTypeNameEnumerator {
    return lazyEnumerator
  }
}