// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlayContextMenuContributor {
  fun contribute(key: InlayHintSettingsKey): ActionGroup?

  companion object {
    val EP_NAME: ExtensionPointName<InlayContextMenuContributor> = ExtensionPointName("com.intellij.codeInsight.inlayContextMenuContributor")
  }
}
