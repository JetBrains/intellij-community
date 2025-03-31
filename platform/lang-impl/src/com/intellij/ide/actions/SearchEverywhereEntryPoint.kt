// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereEntryPoint {
  fun isAvailable(e: AnActionEvent): Boolean
  fun initiateSearchPopup(e: AnActionEvent): Boolean

  companion object {
    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<SearchEverywhereEntryPoint> = ExtensionPointName("com.intellij.searchEverywhere.entryPoint")
  }
}