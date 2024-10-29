// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface FindInDirectoryScopeProvider {
  companion object {
    @JvmField
    val EP_NAME : ExtensionPointName<FindInDirectoryScopeProvider> = ExtensionPointName("com.intellij.findInDirectoryScopeProvider")
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun alterDirectorySearchScope(project: Project,
                                dir: VirtualFile,
                                withSubdirectories: Boolean,
                                previousSearchScope: GlobalSearchScope): GlobalSearchScope
}
