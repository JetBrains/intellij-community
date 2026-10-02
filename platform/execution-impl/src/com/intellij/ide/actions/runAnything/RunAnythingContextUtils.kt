// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RunAnythingContextUtils")

package com.intellij.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.RunAnythingContext.BrowseRecentDirectoryContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ModuleContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.ProjectContext
import com.intellij.ide.actions.runAnything.RunAnythingContext.RecentDirectoryContext
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.annotations.NonNls

fun RunAnythingContext.getPath(): @NonNls String? = when (this) {
  is ProjectContext -> project.guessProjectDir()?.path
                       // Base path may not be present in VFS caches
                       ?: project.basePath
  is ModuleContext -> module.guessModuleDir()?.path
  is RecentDirectoryContext -> path
  is BrowseRecentDirectoryContext -> null
}
