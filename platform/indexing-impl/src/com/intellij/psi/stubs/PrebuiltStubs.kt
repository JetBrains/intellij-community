// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.intellij.util.indexing.FileContent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("Use shared indexes")
interface PrebuiltStubsProvider {
  /**
   * Tries to find stub for [fileContent] in this provider.
   */
  fun findStub(fileContent: FileContent): SerializedStubTree?
}