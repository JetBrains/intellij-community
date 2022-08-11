// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.git.hosting

import com.intellij.collaboration.git.GitRemoteUrlCoordinates
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface HostedRepositoryCoordinates

@ApiStatus.Experimental
interface HostedGitRepositoryMapping {
  val repository: HostedRepositoryCoordinates
  val remote: GitRemoteUrlCoordinates
}
