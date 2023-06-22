// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Internal service to allow Rider extend DirectoryPathMatcher to traverse directories outside index
 * Should be removed then Rider will be able to add directories without content into file index
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class DirectoryPathMatcherService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DirectoryPathMatcherService = project.getService(DirectoryPathMatcherService::class.java)
  }

  open fun shouldProcess(file: VirtualFile): Boolean = false

}