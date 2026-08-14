// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

/**
 * One `.analysisignore` file in the Workspace Model.
 */
@ApiStatus.Internal
interface AnalysisIgnoreEntity : WorkspaceEntity {
  val baseDir: VirtualFileUrl
  val patterns: List<String>
}

@ApiStatus.Internal
object AnalysisIgnoreEntitySource : EntitySource
