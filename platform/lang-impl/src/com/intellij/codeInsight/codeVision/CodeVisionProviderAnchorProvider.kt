// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CodeVisionProviderAnchorProvider {
  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<CodeVisionProviderAnchorProvider> =
      ExtensionPointName("com.intellij.codeInsight.codeVisionProviderAnchorProvider")

    fun getDefaultAnchor(project: Project, provider: CodeVisionProvider<*>): CodeVisionAnchorKind {
      return EP_NAME.computeSafeIfAny { ep ->
        ep.getAnchor(project, provider)
      } ?: provider.defaultAnchor
    }
  }

  fun getAnchor(project: Project, provider: CodeVisionProvider<*>): CodeVisionAnchorKind? = null
}
