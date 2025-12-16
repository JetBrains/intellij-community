// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo

import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.RepositoryId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun Repository.repositoryId(): RepositoryId = RepositoryId.from(project.projectId(), root)