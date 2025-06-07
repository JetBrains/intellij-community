// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectModelContextBridge {
  fun getContext(entry: Module): ModuleContext?
  fun getContext(entry: Library): LibraryContext?
  fun getContext(entry: Sdk): SdkContext?

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectModelContextBridge = project.service()

    suspend fun getInstanceAsync(project: Project): ProjectModelContextBridge = project.serviceAsync()
  }
}