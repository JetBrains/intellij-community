// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.kind

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor

/**
 * Represents an origin of [com.intellij.util.indexing.roots.IndexableFilesIterator].
 */
interface IndexableSetOrigin

/**
 * Marker interface for all origins of roots from project content
 */
interface ContentOrigin : IndexableSetOrigin

/**
 * Marker interface for all origins of roots from project content tied to certain module
 */
interface ModuleContentOrigin : ContentOrigin {
  val module: Module
}

interface ModuleRootOrigin : ModuleContentOrigin {
  override val module: Module
  val roots: List<VirtualFile>?
  val nonRecursiveRoots: List<VirtualFile>?
}

interface LibraryOrigin : IndexableSetOrigin {
  val classRoots: List<VirtualFile>
  val sourceRoots: List<VirtualFile>
}

interface SyntheticLibraryOrigin : IndexableSetOrigin {
  val syntheticLibrary: SyntheticLibrary
  val rootsToIndex: Collection<VirtualFile>
}

interface SdkOrigin : IndexableSetOrigin {
  val sdk: Sdk
  val rootsToIndex: Collection<VirtualFile>
}

interface IndexableSetContributorOrigin : IndexableSetOrigin {
  val indexableSetContributor: IndexableSetContributor
  val rootsToIndex: Set<VirtualFile>
}

interface ProjectFileOrDirOrigin : IndexableSetOrigin {
  val fileOrDir: VirtualFile
}
