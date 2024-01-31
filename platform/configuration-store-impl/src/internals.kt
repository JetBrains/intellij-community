// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CalledInAny

internal const val VERSION_OPTION = "version"

internal fun isSpecialStorage(collapsedPath: String): Boolean =
  collapsedPath == StoragePathMacros.CACHE_FILE || collapsedPath == StoragePathMacros.PRODUCT_WORKSPACE_FILE

@CalledInAny
internal suspend fun ensureFilesWritable(project: Project, files: Collection<VirtualFile>): ReadonlyStatusHandler.OperationStatus =
  withContext(Dispatchers.EDT) {
    ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files)
  }

internal val useBackgroundSave: Boolean
  get() = Registry.`is`("ide.background.save.settings", false)
