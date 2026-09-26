// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

@Suppress("DEPRECATION")
@Deprecated(
  "Renamed to ProjectWideLspClientDescriptor",
  ReplaceWith("ProjectWideLspClientDescriptor", "com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor"),
)
abstract class ProjectWideLspServerDescriptor(project: Project, @NlsSafe presentableName: String) :
  LspServerDescriptor(project, presentableName, *project.getBaseDirectories().toTypedArray())
