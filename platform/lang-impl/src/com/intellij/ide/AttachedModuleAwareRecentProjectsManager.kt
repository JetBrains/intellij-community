// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.project.Project
import com.intellij.platform.ModuleAttachProcessor.Companion.getMultiProjectDisplayName
import kotlinx.coroutines.CoroutineScope

/**
 * Used by IDEs where [attaching modules](https://www.jetbrains.com/help/phpstorm/opening-multiple-projects.html) is supported.
 */
private class AttachedModuleAwareRecentProjectsManager(coroutineScope: CoroutineScope) : RecentProjectsManagerBase(coroutineScope) {
  override fun getProjectDisplayName(project: Project): String? {
    val name = getMultiProjectDisplayName(project)
    return name ?: super.getProjectDisplayName(project)
  }
}