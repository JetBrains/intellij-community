// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun Repository.repositoryId(): RepositoryId = RepositoryId.from(project.projectId(), root)

@ApiStatus.Internal
fun <T : Repository> RepositoryManager<T>.getRepositoryUnlessFresh(root: VirtualFile): T? {
  val repository = getRepositoryForRoot(root)
  return if (repository != null && repository.isFresh) null else repository
}

fun Repository.isHead(hash: Hash): Boolean = currentRevision == hash.asString()