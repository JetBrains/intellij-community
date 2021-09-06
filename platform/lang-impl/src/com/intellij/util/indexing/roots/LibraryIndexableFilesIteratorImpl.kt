// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.LibraryOriginImpl

/**
 * This iterator (instead of [LibraryBridgeIndexableFilesIteratorImpl] is supposed to be used with all API working with old API, not
 * workspace model. Currently it's only [DefaultProjectIndexableFilesContributor]
 */
class LibraryIndexableFilesIteratorImpl(library: Library) : LibraryIndexableFilesIteratorBase(library) {
  override fun getOrigin(): LibraryOrigin {
    return LibraryOriginImpl(library, getRoots())
  }
}