// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui

import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface CodeVisionEntryExtraActionContributor {
  fun contribute(project: Project, groupId: String): List<CodeVisionEntryExtraActionModel>

  fun handle(editor: Editor, groupId: String, actionId: String): Boolean

  fun adText(project: Project, groupId: String): @Nls String? = null

  companion object {
    val EP_NAME: ExtensionPointName<CodeVisionEntryExtraActionContributor> = ExtensionPointName("com.intellij.codeVision.codeVisionEntryExtraActionContributor")
  }
}
