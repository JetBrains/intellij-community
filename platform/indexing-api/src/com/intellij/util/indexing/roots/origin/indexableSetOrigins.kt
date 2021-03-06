// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.kind

import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor

/**
 * Represents an origin of [com.intellij.util.indexing.roots.IndexableFilesIterator].
 */
interface IndexableSetOrigin

interface ModuleRootOrigin : IndexableSetOrigin {
  val module: Module
  val roots: List<VirtualFile>
}

interface LibraryOrigin : IndexableSetOrigin {
  val library: Library
}

interface SyntheticLibraryOrigin : IndexableSetOrigin {
  val syntheticLibrary: SyntheticLibrary
}

interface SdkOrigin : IndexableSetOrigin {
  val sdk: Sdk
}

interface IndexableSetContributorOrigin : IndexableSetOrigin {
  val indexableSetContributor: IndexableSetContributor
}

interface ProjectFileOrDirOrigin : IndexableSetOrigin {
  val fileOrDir: VirtualFile
}
