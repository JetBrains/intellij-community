// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.specialPaths

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SpecialPathsProvider {
  companion object {
    val EP_NAME: ExtensionPointName<SpecialPathsProvider> = ExtensionPointName.create<SpecialPathsProvider>("com.intellij.diagnostic.specialPathsProvider")
  }

  fun collectPaths(project: Project?): List<SpecialPathEntry>
}