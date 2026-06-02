// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile

@Deprecated(
  "Use LspClientDescriptor",
  ReplaceWith("LspClientDescriptor", "com.intellij.platform.lsp.api.LspClientDescriptor"),
)
abstract class LspServerDescriptor protected constructor(
  project: Project,
  @NlsSafe presentableName: String,
  vararg roots: VirtualFile,
) : LspClientDescriptor(project, presentableName, *roots) {

  companion object {
    @Deprecated("Use LspClientDescriptor.LOG", ReplaceWith("LspClientDescriptor.LOG", "com.intellij.platform.lsp.api.LspClientDescriptor"))
    @JvmField
    val LOG: Logger = LspClientDescriptor.LOG

    @Deprecated(
      "Use LspClientDescriptor.getLanguageId",
      ReplaceWith("LspClientDescriptor.getLanguageId(file)", "com.intellij.platform.lsp.api.LspClientDescriptor"),
    )
    fun getLanguageId(file: VirtualFile): String = LspClientDescriptor.getLanguageId(file)
  }
}
