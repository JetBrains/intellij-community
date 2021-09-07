// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.LibraryIdOriginImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId

/**
 * This iterator (instead of [LibraryIndexableFilesIteratorImpl] is supposed to be used with all API working with workspace model
 */
class LibraryBridgeIndexableFilesIteratorImpl(library: Library, private val libraryId: LibraryId) :
  LibraryIndexableFilesIteratorBase(library) {

  constructor(library: LibraryBridge): this(library, library.libraryId)

  override fun getOrigin(): LibraryOrigin {
    return LibraryIdOriginImpl(library, libraryId)
  }
}