// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

/**
 * One `.analysisignore` file in the Workspace Model. An entity of [AnalysisIgnoreDefaultEntitySource] holds the defaults of a project
 * root instead.
 */
@ApiStatus.Internal
interface AnalysisIgnoreEntity : WorkspaceEntity {
  val baseDir: VirtualFileUrl
  val patterns: List<String>
}

@ApiStatus.Internal
object AnalysisIgnoreEntitySource : EntitySource

/**
 * The source of an entity without a `.analysisignore` file. A project root holds one such entity with the
 * [default lines][AnalysisIgnoreDefaults.lines] while the root directory holds no file. The file in the root directory removes the entity.
 */
@ApiStatus.Internal
object AnalysisIgnoreDefaultEntitySource : EntitySource
