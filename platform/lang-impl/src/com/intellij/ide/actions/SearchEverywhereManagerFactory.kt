// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereManagerFactory {
  fun isAvailable(): Boolean
  fun getManager(project: Project): SearchEverywhereManager

  companion object {
    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<SearchEverywhereManagerFactory> = ExtensionPointName("com.intellij.searchEverywhere.manager")
  }
}