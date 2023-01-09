// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion.collectFiles
import com.intellij.util.indexing.roots.kind.IndexableSetIterableOrigin
import com.intellij.util.indexing.roots.origin.LibraryIterableOriginImpl

object IterableOriginsMethods {
  fun createLibraryOrigin(library: Library): IndexableSetIterableOrigin {
    val classFiles = collectFiles(library, OrderRootType.CLASSES, null)
    val sourceFiles = collectFiles(library, OrderRootType.SOURCES, null)
    return LibraryIterableOriginImpl(classFiles, sourceFiles, listOf(*(library as LibraryEx).excludedRoots),
                                     library.getName(), library.getPresentableName())
  }
}