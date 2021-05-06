// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.util.indexing.FileContent

private val prebuiltStubsProvider: FileTypeExtension<PrebuiltStubsProvider> =
  FileTypeExtension<PrebuiltStubsProvider>("com.intellij.filetype.prebuiltStubsProvider")

@Deprecated("Use shared indexes")
interface PrebuiltStubsProvider {
  /**
   * Tries to find stub for [fileContent] in this provider.
   */
  fun findStub(fileContent: FileContent): SerializedStubTree?
}