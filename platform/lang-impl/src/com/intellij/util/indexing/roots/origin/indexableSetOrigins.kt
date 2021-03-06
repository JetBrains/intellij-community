// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.kind

import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor

class ModuleRootOriginImpl(override val module: Module,
                           override val roots: List<VirtualFile>) : ModuleRootOrigin

class LibraryOriginImpl(override val library: Library) : LibraryOrigin

class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary) : SyntheticLibraryOrigin

class SdkOriginImpl(override val sdk: Sdk) : SdkOrigin

class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor) : IndexableSetContributorOrigin

class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin